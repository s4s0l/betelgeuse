/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-06 23:48
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.HackedActor
import akka.actor.{Actor, ActorLogging, Cancellable}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
trait TimeoutActor extends HackedActor with ActorLogging {

  final val TIMEOUT_DELAY_ALWAYS: Any => Boolean = _ => true
  final val TIMEOUT_DELAY_NEVER: Any => Boolean = _ => false

  val timeoutAutoDelayOnMessage: Any => Boolean = TIMEOUT_DELAY_ALWAYS

  implicit val timeoutScheduleDispatcher: ExecutionContextExecutor = context.dispatcher

  val timeoutTime: FiniteDuration = 10 minutes

  val timeoutSelfMessage: Any = akka.actor.ReceiveTimeout.getInstance

  val timeoutHandler: () => Unit = () => {
    log.debug("Actor timed out, stopping.")
    context.stop(self)
  }



  val timeoutHandlerReceiver: Actor.Receive = {
    case x if x == timeoutSelfMessage => timeoutHandler()
  }

  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    if (timeoutHandlerReceiver.isDefinedAt(msg)) {
      cancelTimeoutTimer()
      super.aroundReceive(timeoutHandlerReceiver, msg)
    } else if (receive.isDefinedAt(msg)) {
      super.aroundReceive(receive, msg)
      if (timeoutAutoDelayOnMessage(msg)) {
        timeoutDelay()
      }
    } else {
      super.aroundReceive(receive, msg)
    }
  }

  private var timeoutTimer: Option[Cancellable] = None

  final def timeoutDelay(duration: FiniteDuration = timeoutTime): Unit = {
    cancelTimeoutTimer()
    timeoutTimer = Some(context.system.scheduler.scheduleOnce(timeoutTime, self, timeoutSelfMessage))
  }

  final def cancelTimeoutTimer(): Unit = {
    timeoutTimer.map { t =>
      t.cancel()
    }
    timeoutTimer = None
  }

  def timeoutInitialStart(): Unit = {
    timeoutDelay()
  }

  override def preStart(): Unit = {
    super.preStart()
    timeoutInitialStart()
  }


}
