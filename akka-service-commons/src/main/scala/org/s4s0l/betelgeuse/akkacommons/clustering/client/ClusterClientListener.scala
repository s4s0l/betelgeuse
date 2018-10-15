/*
 * Copyright© 2018 the original author or authors.
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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import akka.actor.{Actor, ActorLogging, ActorPath, Timers}
import akka.cluster.client.{ContactPointAdded, ContactPointRemoved, ContactPoints}
import org.s4s0l.betelgeuse.akkacommons.clustering.client.ClusterClientListener.{CoolDownEnded, Echo, SendEcho}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class ClusterClientListener(target: ClusterClientTarget, callback: () => Unit)
  extends Actor
    with Timers
    with ActorLogging {

  def receive: Receive =
    receiveWithContactPoints(Set.empty)

  def receiveWithContactPoints(contactPoints: Set[ActorPath]): Receive = {
    case ContactPoints(_) ⇒
      context.become(probablyAvailable())
    case ContactPointAdded(_) ⇒
      context.become(probablyAvailable())
    case ContactPointRemoved(_) ⇒

  }

  def probablyAvailable(): Receive = {
    timers.startPeriodicTimer("TIMER", SendEcho(), 1.second)
    target.send("/user/bg-receptionist-echo", Echo())

    {
      case SendEcho() =>
        target.send("/user/bg-receptionist-echo", Echo())
      case Echo() =>
        callback()
        timers.cancel("TIMER")
        timers.startSingleTimer("COOL_DOWN", CoolDownEnded(), 2.seconds)
        context.become(coolDown())
    }
  }

  /**
    * coolDown is just to prevent unhandled echo responses (they are buffered on
    * client when there is no receptionist)
    *
    * @return
    */
  def coolDown(): Receive = {
    case CoolDownEnded() =>
      context.stop(self)
    case Echo() =>

  }

}

object ClusterClientListener {

  case class Echo() extends JacksonJsonSerializable

  case class SendEcho()

  case class CoolDownEnded()

}
