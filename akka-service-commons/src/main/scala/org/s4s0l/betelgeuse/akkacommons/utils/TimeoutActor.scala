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
