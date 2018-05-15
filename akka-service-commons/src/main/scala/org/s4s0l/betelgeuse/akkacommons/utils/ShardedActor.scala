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


package org.s4s0l.betelgeuse.akkacommons.utils

import akka.HackedActor
import akka.actor.ActorLogging
import akka.cluster.sharding.ShardRegion.Passivate

/**
  * @author Marcin Wielgus
  */
trait ShardedActor extends HackedActor with ActorLogging {

  def shardName: String = self.path.parent.parent.name

  def shardedActorId: String = self.path.name

  private var passivationInProgress = false

  lazy val shardedActorIdSplit: (String, String) = {
    val underScoreIndex = shardedActorId.indexOf('_')
    (shardedActorId.substring(0, underScoreIndex),
      shardedActorId.substring(underScoreIndex + 1))
  }

  def shardedPassivate(): Unit = {
    onPassivationRequest()
    context.parent ! Passivate(stopMessage = PassivationCallback)
    passivationInProgress = true
  }

  override protected def hackedAroundReceive(receive: Receive, msg: Any): Unit = {
    if (passivationInProgress && handlePassivation.isDefinedAt(msg)) {
      handlePassivation.apply(msg)
    } else {
      super.hackedAroundReceive(receive, msg)
    }
  }

  def onPassivationRequest(): Unit = {}

  private def handlePassivation: Receive = {
    case PassivationCallback =>
      onPassivationCallback()
      context.stop(self)
      log.debug("Actor passivation callback received, stopping.")
  }

  def onPassivationCallback(): Unit = {}
}
