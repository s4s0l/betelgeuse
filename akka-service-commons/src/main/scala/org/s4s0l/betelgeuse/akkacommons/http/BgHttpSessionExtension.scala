/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

package org.s4s0l.betelgeuse.akkacommons.http

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.softwaremill.session.SessionDirectives.{optionalSession, setSession}
import com.softwaremill.session.SessionOptions.{oneOff, usingCookies}
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.s4s0l.betelgeuse.akkacommons.http.BgHttpSessionExtension.SessionContext
import org.s4s0l.betelgeuse.akkacommons.http.SessionActor.{SessionActorSettings, SessionChildActorFactory}
import org.s4s0l.betelgeuse.akkacommons.http.SessionManagerActor.Protocol
import org.s4s0l.betelgeuse.utils.UuidUtils

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BgHttpSessionExtension(private val system: ExtendedActorSystem) extends Extension {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private lazy val httpSessionConfig = SessionConfig.default("some_very_long_secret_and_random_string_some_very_long_secret_and_random_string")
  final implicit lazy val httpSessionManager: SessionManager[String] = new SessionManager[String](httpSessionConfig)


  def createSessionProtocol(factory: SessionChildActorFactory, managerName: String = "sessionManager"): Protocol = {
    import scala.concurrent.duration._
    val props = SessionManagerActor.props(SessionActorSettings(httpSessionConfig.sessionMaxAgeSeconds.get seconds), factory)
    val sessionManagerActor = system.actorOf(props, managerName)
    LOGGER.debug(s"Session manager created at $managerName")
    SessionManagerActor.protocol(sessionManagerActor)
  }

  /**
    * boolean says if session is newly created
    */
  def httpSessionGuardedWithActors(protocol: Protocol)
                                  (route: SessionContext => Route): Route = {

    optionalSession(oneOff, usingCookies) {
      case Some(session) =>
        protocol.tick(session)
        route(SessionContext(newSession = false, session, protocol))
      case None =>
        val newSession = UuidUtils.timeBasedUuid().toString
        LOGGER.info("Session created")
        setSession(oneOff, usingCookies, newSession) {
          protocol.tick(newSession)
          route(SessionContext(newSession = true, newSession, protocol))
        }
    }
  }

  def httpSessionGuardedSimple(route: String => Route): Route = {
    optionalSession(oneOff, usingCookies) {
      case Some(session) => route(session)
      case None =>
        val newSession = UUID.randomUUID().toString
        setSession(oneOff, usingCookies, newSession) {
          route(newSession)
        }
    }
  }

}


object BgHttpSessionExtension extends ExtensionId[BgHttpSessionExtension] with ExtensionIdProvider {

  @SerialVersionUID(1L) final case class NamedPut(name: String, ref: ActorRef)

  override def get(system: ActorSystem): BgHttpSessionExtension = system.extension(this)

  override def apply(system: ActorSystem): BgHttpSessionExtension = system.extension(this)

  override def lookup(): BgHttpSessionExtension.type = BgHttpSessionExtension

  override def createExtension(system: ExtendedActorSystem): BgHttpSessionExtension =
    new BgHttpSessionExtension(system)


  case class SessionContext(newSession: Boolean, sessionId: String, protocol: Protocol) {
    def tell(sessionType: String, message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
      protocol.tell(sessionId, sessionType, message)
    }

    def ask(sessionType: String, message: Any)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] = {
      protocol.ask(sessionId, sessionType, message)
    }
  }

}