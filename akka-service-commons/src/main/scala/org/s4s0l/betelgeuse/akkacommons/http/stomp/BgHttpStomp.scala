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



package org.s4s0l.betelgeuse.akkacommons.http.stomp

import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.http.BgHttp
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandler.{StompHandlerEnvironment, StompHandlerSettings}
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandlerApi.{StompClientMessage, StompServerMessage}

/**
  * @author Marcin Wielgus
  */
trait BgHttpStomp extends BgHttp {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgHttpStomp])


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with pubsub.conf with fallback to...")
    ConfigFactory.parseResources("clustering-pubsub.conf").withFallback(super.customizeConfiguration)
  }

  implicit def stompExtension: BgHttpStompExtension =
    BgHttpStompExtension.get(system)


  /**
    * creates stomp flow in http creating an actor that handles all stomp messages coming from
    * web socket, all messages send to this actor can be a [[StompServerMessage]] or any other message.
    * It will be passed to the web socket if they match subscription. For Any other message
    * settings contain function for converting them to [[StompServerMessage]].
    *
    * @param flow flow factory from environment that gives access to stomp actor reference and current session Id
    *             if you want to send some messages remember to set sender to this actor if you want to
    *             receive responses
    */
  def stomp(settings: StompHandlerSettings, flow: StompHandlerEnvironment => Flow[StompClientMessage, Option[StompServerMessage], Any]): Route = {
    StompHandler.socksStompFlow(settings, flow)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BgHttpStompExtension)
    LOGGER.info("Initializing done.")
  }

}

