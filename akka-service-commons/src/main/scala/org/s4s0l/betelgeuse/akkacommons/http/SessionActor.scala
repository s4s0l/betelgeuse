/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 10:11
 */

package org.s4s0l.betelgeuse.akkacommons.http

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.s4s0l.betelgeuse.akkacommons.http.SessionActor._
import org.s4s0l.betelgeuse.akkacommons.utils.TimeoutActor

import scala.concurrent.duration.FiniteDuration

/**
  * @author Marcin Wielgus
  */
class SessionActor(sessionId: String, settings: SessionActorSettings, sessionChildActorFactory: SessionChildActorFactory) extends Actor with ActorLogging with TimeoutActor {

  private var children = Map[String, ActorRef]()

  override val timeoutTime: FiniteDuration = settings.sessionTimeout

  private def createChildActor(typ: String) = {
    val actr = context.actorOf(sessionChildActorFactory.apply(SessionChildActorParams(typ, sessionId)), typ)
    context.watchWith(actr, SessionTypeActorTerminated(typ, actr))
    children = children + (typ -> actr)
    actr
  }

  private def getTargetRef(typ: String) = {
    children.getOrElse(typ, createChildActor(typ))
  }

  override def receive: Receive = {
    case TickMessage(_, _) =>
    case SessionMessage(_, typ, msg) =>
      getTargetRef(typ).forward(msg)
    case SessionTypeActorTerminated(t, _) =>
      children = children - t
  }

}

object SessionActor {

  def props(sessionId: String, settings: SessionActorSettings, sessionChildActorFactory: SessionChildActorFactory): Props =
    Props(new SessionActor(sessionId, settings, sessionChildActorFactory))

  case class SessionChildActorParams(sessionScopeActorType: String, sessionId: String)

  type SessionChildActorFactory = PartialFunction[SessionChildActorParams, Props]

  case class SessionActorSettings(sessionTimeout: FiniteDuration)

  case class TickMessage(sessionId: String, when: Date = new Date())

  case class SessionMessage(sessionId: String, sessionScopeActorType: String, msg: Any)

  case class SessionTypeActorTerminated(sessionScopeActorType: String, actorRef: ActorRef)


}
