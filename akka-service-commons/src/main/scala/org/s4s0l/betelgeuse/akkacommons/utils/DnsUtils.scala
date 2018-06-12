/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

/*
 * Copyright© 2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package org.s4s0l.betelgeuse.akkacommons.utils

import java.net.InetAddress
import java.util

import akka.actor.{ActorPath, Address, RootActorPath}
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
object DnsUtils {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)


  val getCurrentNodeHostName: String = {
    //on shippable UnknownHostException is thrown!
    val fromInetAddress = try {
      Some(InetAddress.getLocalHost.getHostName)
    } catch {
      case ex: java.net.UnknownHostException =>
        LOGGER.warn("Unable to resolve host name from InetAddress.getLocalHost.getHostName, will try $HOSTNAME", ex)
        None
    }
    fromInetAddress.orElse {
      Option(System.getenv("HOSTNAME"))
    }.getOrElse {
      throw new Exception("Unable to determine current node host name!")
    }
  }

  /**
    * gets and ip address from hostname, gets current hostname then does dns lookup
    * on dnsLookupAddress and tries to find ip among srv records.
    *
    */
  def getSelfIpAddressFromHostName(dnsLookupAddress: Address): String = {
    AllUtils.tryNTimes(s"IpAddressFromHost:${dnsLookupAddress}", 10, exceptionProducer = AllUtils.tryNTimesExceptionFactory("Unable to detect current host ip address")) {
      import scala.collection.JavaConverters._
      val allByName = InetAddress.getAllByName(dnsLookupAddress.host.get)
      val allByNameLookup = util.Arrays.asList(allByName: _*).asScala.map { it => (it, List(it.getCanonicalHostName, it.getHostAddress, it.getHostName)) }
      val selfAddresses = getAllLocalIpAddresses.toList
      LOGGER.info(s"Dns addresses all by name: $allByNameLookup, self host names: $selfAddresses")
      val myHost = allByNameLookup.find(x => x._2.intersect(selfAddresses).nonEmpty).map {
        _._1.getHostAddress
      }
      myHost.getOrElse(throw new Exception(s"Unable to find current host names $selfAddresses"))
    }
  }

  def getAllLocalIpAddresses: Set[String] = {
    import java.net.{InetAddress, NetworkInterface}
    import java.util

    import scala.collection.JavaConverters._
    val localhost = InetAddress.getLocalHost
    // Just in case this host has multiple IP addresses....
    val allMyIps = InetAddress.getAllByName(localhost.getCanonicalHostName)
    val localHostAddresses = if (allMyIps != null && allMyIps.length > 1) {
      util.Arrays.asList(allMyIps: _*).asScala.flatMap { it => List(it.getCanonicalHostName, it.getHostAddress, it.getHostName) }
    } else {
      List(localhost.getCanonicalHostName, localhost.getHostAddress, localhost.getHostName)
    }
    val fromInterfaces = NetworkInterface.getNetworkInterfaces.asScala
      .flatMap(_.getInetAddresses.asScala)
      .flatMap { it => List(it.getCanonicalHostName, it.getHostAddress, it.getHostName) }
    (localHostAddresses ++ fromInterfaces ++ List(DnsUtils.getCurrentNodeHostName)).toSet
  }

  def lookupActorPaths(candidates: Seq[ActorPath])(implicit executor: ExecutionContext): Future[Seq[ActorPath]] = {
    import scala.collection.JavaConverters._
    val mm: Seq[Future[Seq[ActorPath]]] = candidates
      .map(x => {
        LOGGER.debug("Looking up " + x.address.host.get)
        (x, x.address.host.get)
      })
      .map(x => Future {
        val ret = util.Arrays.asList(InetAddress.getAllByName(x._2): _*).asScala
        ret
      }
        .map {
          resolved: Seq[InetAddress] =>
            LOGGER.debug("Looked up " + resolved.map(_.getHostAddress))
            resolved.map(a => {
              val newAddr = Address.apply(x._1.address.protocol, x._1.address.system, a.getHostAddress, x._1.address.port.get)
              ActorPath.fromString(newAddr + x._1.toStringWithoutAddress)
            })
        })
    Future.sequence(mm).map((x: Seq[Seq[ActorPath]]) => x.flatten)
  }

  def lookupAddresses(candidates: Seq[Address])(implicit executor: ExecutionContext): Future[Seq[Address]] = {
    lookupActorPaths(candidates.map(x => RootActorPath(x)))(executor).map(x => x.map(_.address))
  }
}
