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

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.language.implicitConversions

/**
  * utility trait that hides details about how we reach actor
  *
  * @author Marcin Wielgus
  */

trait ActorTarget {
  def !(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit

  def ?(msg: Any)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any]
}

object ActorTarget {

  implicit def toRefTarget(actorRef: ActorRef): ActorTarget = new RefTarget(() => actorRef)

  class RefTarget(private val ref: () => ActorRef) extends ActorTarget {
    override def !(message: Any)(implicit sender: ActorRef): Unit = {
      ref().tell(message, sender)
    }

    override def ?(msg: Any)(implicit timeout: Timeout, sender: ActorRef): Future[Any] = {
      ref().ask(msg)(timeout, sender)
    }
  }

}
