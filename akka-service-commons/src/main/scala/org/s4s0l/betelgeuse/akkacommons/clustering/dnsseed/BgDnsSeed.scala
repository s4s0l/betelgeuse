/*
 * Copyright© 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.dnsseed

import java.net.InetAddress
import java.util

import akka.actor.{Address, AddressFromURIString, Cancellable}
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BgClustering
import org.s4s0l.betelgeuse.akkacommons.utils.DnsUtils

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
trait BgDnsSeed extends BgClustering {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgDnsSeed])
  private lazy val vipDockerMode = if (System.getProperty("akka.cluster.dns.vipMode", "false") == "true") "tasks." else ""
  private lazy val dnsLookup =
    System.getProperty("akka.cluster.dns.name", s"akka.tcp://${serviceInfo.id.systemName}@$vipDockerMode${serviceInfo.id.systemName}_service:1${serviceInfo.firstPortSuffix}")
  private lazy val dnsLookupAddress: Address = AddressFromURIString.apply(dnsLookup)
  private var scheduler: Cancellable = _


  abstract override def customizeConfiguration: Config = {
    if (serviceInfo.docker || System.getProperty("akka.cluster.dns.name") != null) {
      LOGGER.info("Will customize seeding nodes with dns entries")
      val selfHostName = DnsUtils.getCurrentNodeHostName
      val current = util.Arrays.asList(InetAddress.getAllByName(dnsLookupAddress.host.get): _*).asScala
      val mehost = current.find(x => x.getCanonicalHostName == selfHostName).get.getHostAddress
      val sysName = s"bg.info.externalAddress = $mehost"
      LOGGER.info(s"bg.info.externalAddress will be set to $mehost")
      LOGGER.info("Customize config with clustering-dns-seed.conf with fallback to...")
      ConfigFactory.parseString(sysName)
        .withFallback(ConfigFactory.parseResources("clustering-dns-seed.conf").withFallback(super.customizeConfiguration))
    } else {
      super.customizeConfiguration
    }
  }


  abstract override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    if (serviceInfo.docker || System.getProperty("akka.cluster.dns.name") != null) {
      val selfAddress = cluster.selfAddress
      val selfSeedingNums = config.getIntList("akka.cluster.dns.seeding-num")
      val selfSeeding = selfSeedingNums.contains(config.getInt("akka.cluster.dns.num"))
      val selfSeedingAddresses = if (selfSeeding) List(selfAddress) else List()

      scheduler = system.scheduler.schedule(Duration.Zero, 30 seconds, () => {
        LOGGER.debug(s"Cluster bootstrap, self address: $selfAddress")
        DnsUtils.lookupAddresses(List(dnsLookupAddress)).onComplete {
          case Success(a) =>
            val sorted = a.sortWith(_.host.getOrElse("") < _.host.getOrElse(""))
            val others = sorted filter { address =>
              address != selfAddress
            }
            val serviceSeeds: Seq[Address] = selfSeedingAddresses ++ others
            LOGGER.debug(s"Cluster bootstrap, found service seeds: $serviceSeeds")
            cluster.joinSeedNodes(serviceSeeds.asInstanceOf[immutable.Seq[Address]])
            if (selfSeeding) {
              scheduler.cancel()
              scheduler = null
            }
          case Failure(t) => //throw the error
            LOGGER.debug("????", t)

        }
      })
    }
    LOGGER.info("Initializing done.")
  }


  /**
    * called when current node is up in cluster
    */
  abstract override protected def onClusterUp(): Unit = {
    super.onClusterUp()
    if (scheduler != null)
      scheduler.cancel()
  }


}
