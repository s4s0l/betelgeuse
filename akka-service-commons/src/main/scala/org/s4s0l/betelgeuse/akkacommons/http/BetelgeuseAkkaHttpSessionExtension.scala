/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 12:28
 */

package org.s4s0l.betelgeuse.akkacommons.http

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.server.Route
import akka.pattern.AskableActorRef
import akka.util.Timeout
import com.softwaremill.session.SessionDirectives.{optionalSession, setSession}
import com.softwaremill.session.SessionOptions.{oneOff, usingCookies}
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.s4s0l.betelgeuse.akkacommons.http.BetelgeuseAkkaHttpSessionExtension.SessionContext
import org.s4s0l.betelgeuse.akkacommons.http.SessionActor.{SessionActorSettings, SessionChildActorFactory}
import org.s4s0l.betelgeuse.akkacommons.http.SessionManagerActor.Protocol

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaHttpSessionExtension(private val system: ExtendedActorSystem) extends Extension {
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
        val newSession = UUID.randomUUID().toString
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


object BetelgeuseAkkaHttpSessionExtension extends ExtensionId[BetelgeuseAkkaHttpSessionExtension] with ExtensionIdProvider {

  @SerialVersionUID(1L) final case class NamedPut(name: String, ref: ActorRef)

  override def get(system: ActorSystem): BetelgeuseAkkaHttpSessionExtension = system.extension(this)

  override def apply(system: ActorSystem): BetelgeuseAkkaHttpSessionExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaHttpSessionExtension.type = BetelgeuseAkkaHttpSessionExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaHttpSessionExtension =
    new BetelgeuseAkkaHttpSessionExtension(system)


  case class SessionContext(newSession: Boolean, sessionId: String, protocol: Protocol) {
    def tell(sessionType: String, message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
      protocol.tell(sessionId, sessionType, message)
    }

    def ask(sessionType: String, message: Any)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] = {
      protocol.ask(sessionId, sessionType, message)
    }
  }

}