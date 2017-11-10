/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 23:09
 *
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