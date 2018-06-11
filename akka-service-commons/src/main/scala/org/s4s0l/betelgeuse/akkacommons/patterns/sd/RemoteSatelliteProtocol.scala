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

package org.s4s0l.betelgeuse.akkacommons.patterns.sd

import akka.actor.ActorRef
import akka.serialization.Serialization
import org.s4s0l.betelgeuse.akkacommons.patterns.message.Message
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.ValidationError
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
/**
  * Adapts satellite protocol for remote message passing via [[org.s4s0l.betelgeuse.akkacommons.patterns.message.Message]] pattern
  *
  * @param actorTarget      actor to ask, should respond with Messages also
  * @param simpleSerializer serializer to be used for marshalling T
  */
private[sd] class RemoteSatelliteProtocol[T <: AnyRef](actorTarget: ActorTarget)
                                                      (implicit simpleSerializer: Serialization)
  extends SatelliteProtocol[T] {
  /**
    * distributes state change
    */
  override def stateChange(stateChangeMessage: StateChange[T])
                          (implicit executionContext: ExecutionContext, sender: ActorRef)
  : Future[StateChangeResult] = {
    actorTarget.?(stateChangeMessage.toMessage)(stateChangeMessage.expectedConfirmIn, sender).map {
      case msg@Message("state-change-ok", _, _, _) =>
        StateChangeOk(msg.correlationId)
      case msg@Message("state-change-validation-ok", _, _, _) =>
        StateChangeOkWithValidationError(msg.correlationId, msg.payload.deserializeTo[ValidationError])
      case msg@Message("state-change-not-ok", _, _, _) =>
        StateChangeNotOk(msg.correlationId, new Exception(s"Remote satelliteError: ${msg.failedOpt.getOrElse(-1)}, message was: ${msg.payload.asString}"))
      case _ =>
        StateChangeNotOk(stateChangeMessage.messageId, new Exception(s"Remote satellite unknown response error."))
    }.recover { case ex: Throwable => StateChangeNotOk(stateChangeMessage.messageId, new Exception(ex)) }
  }

  /**
    * informs that all destinations confirmed
    */
  override def distributionComplete(distributionCompleteMessage: DistributionComplete)(implicit executionContext: ExecutionContext, sender: ActorRef)
  : Future[DistributionCompleteResult] = {

    actorTarget.?(distributionCompleteMessage.toMessage)(distributionCompleteMessage.expectedConfirmIn, sender).map {
      case msg@Message("distribution-complete-ok", _, _, _) =>
        DistributionCompleteOk(msg.correlationId)
      case msg@Message("distribution-complete-not-ok", _, _, _) =>
        DistributionCompleteNotOk(msg.correlationId, new Exception(s"Remote satelliteError: ${msg.failedOpt.getOrElse(-1)}, message was: ${msg.payload.asString}"))
      case _ =>
        DistributionCompleteNotOk(distributionCompleteMessage.messageId, new Exception(s"Remote satellite unknown response error."))
    }.recover { case ex: Throwable => DistributionCompleteNotOk(distributionCompleteMessage.messageId, new Exception(ex)) }
  }
}
