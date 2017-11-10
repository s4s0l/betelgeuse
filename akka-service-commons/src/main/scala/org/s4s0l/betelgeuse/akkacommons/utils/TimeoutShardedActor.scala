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



package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.Actor
import akka.cluster.sharding.ShardRegion.Passivate

/**
  * @author Marcin Wielgus
  */
trait TimeoutShardedActor extends TimeoutActor with ShardedActor {


  final override val timeoutHandler: () => Unit = () => {
    throw new RuntimeException("Unsupported, should not happen, timeoutHandlerReceiver is overrided so it should never be called!")
  }

  def onPasivationRequest():Unit ={}

  def onPassivationCallback() : Unit= {}

  override val timeoutHandlerReceiver: Actor.Receive = {
    case x if x == timeoutSelfMessage =>
      log.debug("Actor timed out, passivating.")
      onPasivationRequest()
      context.parent ! Passivate(stopMessage = PassivationCallback)
    case PassivationCallback =>
      onPassivationCallback()
      context.stop(self)
      log.debug("Actor passivation callback received, stopping.")
  }
}

case object PassivationCallback