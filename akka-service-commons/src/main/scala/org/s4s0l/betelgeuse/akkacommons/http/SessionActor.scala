/*
 * Copyright© 2017 the original author or authors.
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
