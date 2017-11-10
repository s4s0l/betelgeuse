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
import akka.pattern.AskableActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.http.SessionActor.{SessionActorSettings, SessionChildActorFactory, SessionChildActorParams, SessionTypeActorTerminated}

import scala.concurrent.Future

/**
  * Keeps track of session scoped actors
  *
  * @author Marcin Wielgus
  */
class SessionManagerActor(settings: SessionActorSettings, factory: SessionChildActorFactory) extends Actor with ActorLogging {

  private var children = Map[String, ActorRef]()

  private def createChildActor(sessionId: String) = {
    val actr = context.actorOf(SessionActor.props(sessionId, settings, factory), sessionId)
    context.watchWith(actr, SessionTypeActorTerminated(sessionId, actr))
    children = children + (sessionId -> actr)
    actr
  }

  private def getTargetRef(typ: String) = {
    children.getOrElse(typ, createChildActor(typ))
  }

  override def receive: Receive = {
    case SessionTypeActorTerminated(sess, _) =>
      children = children - sess
    case t@SessionActor.TickMessage(sess, _) =>
      children.get(sess).foreach(_.forward(t))
    case m@SessionActor.SessionMessage(sess, t, _) =>
      val scap = SessionChildActorParams(t, sess)
      if (!factory.isDefinedAt(scap)) {
        //we fail as fast as possible this is code error
        throw new RuntimeException(s"Undefined session child actor at $scap")
      }
      getTargetRef(sess).forward(m)
  }
}

object SessionManagerActor {
  def props(settings: SessionActorSettings, factory: SessionChildActorFactory): Props = Props(new SessionManagerActor(settings, factory))


  def protocol(actorRef: ActorRef): Protocol = Protocol(actorRef)

  case class Protocol(actorRef: ActorRef) {
    def tell(sessionId: String, sessionType: String, message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
      actorRef ! SessionActor.SessionMessage(sessionId, sessionType, message)
    }

    def ask(sessionId: String, sessionType: String, message: Any)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] = {
      new AskableActorRef(actorRef) ? SessionActor.SessionMessage(sessionId, sessionType, message)
    }

    def tick(sessionId: String)(implicit sender: ActorRef = Actor.noSender): Unit = {
      actorRef ! SessionActor.TickMessage(sessionId, new Date)
    }
  }

}
