/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-04 08:36
 *
 */

package org.s4s0l.betelgeuse.akkacommons.http.stomp

import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.http.BetelgeuseAkkaHttp
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandler.{StompHandlerEnvironment, StompHandlerSettings}
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandlerApi.{StompClientMessage, StompServerMessage}

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaHttpStomp extends BetelgeuseAkkaHttp {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaHttpStomp])


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with pubsub.conf with fallback to...")
    ConfigFactory.parseResources("clustering-pubsub.conf").withFallback(super.customizeConfiguration)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BetelgeuseAkkaHttpStompExtension)
    LOGGER.info("Initializing done.")
  }



  def stomp(settings: StompHandlerSettings, flow: StompHandlerEnvironment => Flow[StompClientMessage, Option[StompServerMessage], Any]): Route = {
    StompHandler.socksStompFlow(settings, flow)
  }


  def stompExtension: BetelgeuseAkkaHttpStompExtension =
    BetelgeuseAkkaHttpStompExtension.get(system)

}

