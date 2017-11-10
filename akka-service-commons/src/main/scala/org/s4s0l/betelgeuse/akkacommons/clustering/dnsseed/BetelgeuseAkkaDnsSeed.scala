/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-12 20:16
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.dnsseed

import java.net.InetAddress
import java.util

import akka.actor.{Address, AddressFromURIString, Cancellable}
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BetelgeuseAkkaClustering
import org.s4s0l.betelgeuse.akkacommons.utils.DnsUtils

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaDnsSeed extends BetelgeuseAkkaClustering {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaDnsSeed])
  private lazy val vipDockerMode = if (System.getProperty("akka.cluster.dns.vipMode", "false") == "true") "tasks." else ""
  private lazy val dnsLookup =
    System.getProperty("akka.cluster.dns.name", s"akka.tcp://${serviceInfo.serviceName}@$vipDockerMode${serviceInfo.serviceName}_service:1${serviceInfo.firstPortSuffix}")
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
