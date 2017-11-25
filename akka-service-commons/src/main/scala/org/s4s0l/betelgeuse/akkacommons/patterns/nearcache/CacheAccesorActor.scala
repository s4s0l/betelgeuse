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

package org.s4s0l.betelgeuse.akkacommons.patterns.nearcache

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccesorActor.Protocol.{DefaultIncomingMessage, DefaultOutgoingMessage}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccesorActor.Settings

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class CacheAccesorActor(settings: Settings) extends Actor with ActorLogging {

  override def receive: Actor.Receive = {
    case _: DefaultIncomingMessage =>
  }


}


object CacheAccesorActor {
  /**
    * creates props for actor
    */
  def props(settings: Settings): Props = {
    Props(new CacheAccesorActor(settings))
  }

  final case class Settings()

  /**
    * An protocol for [[CacheAccesorActor]]
    */
  final class Protocol private(actorRef: => ActorRef) {

    import concurrent.duration._

    def defaultMessage(msg: DefaultIncomingMessage): Unit = actorRef ! msg

    def defaultMessageAsk(msg: DefaultIncomingMessage): Future[DefaultOutgoingMessage] =

      actorRef.ask(msg)(5 seconds).mapTo
  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply(actorRef: => ActorRef): Protocol = new Protocol(actorRef)

    sealed trait IncomingMessage

    sealed trait OutgoingMessage

    //outgoing

    case class DefaultIncomingMessage() extends IncomingMessage

    case class DefaultOutgoingMessage() extends OutgoingMessage

  }


}    