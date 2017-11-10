/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-12 20:16
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import java.net.InetAddress
import java.util

import akka.actor.{ActorPath, Address, RootActorPath}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
object DnsUtils {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)


  def getCurrentNodeHostName:String = InetAddress.getLocalHost.getHostName

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
