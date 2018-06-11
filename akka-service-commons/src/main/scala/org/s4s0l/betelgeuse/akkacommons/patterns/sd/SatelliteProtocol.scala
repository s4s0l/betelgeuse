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
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.Headers
import org.s4s0l.betelgeuse.akkacommons.patterns.message.{Message, Payload}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.ValidationError
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA
import org.s4s0l.betelgeuse.akkacommons.utils.QA._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
object SatelliteProtocol {


  sealed trait DistributionCompleteResult extends Result[Uuid, Null]

  sealed trait StateChangeResult extends Result[Uuid, Null]

  case class StateChange[T <: AnyRef](versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration, messageId: Uuid = QA.uuid) extends UuidQuestion {
    def toMessage(implicit simpleSerializer: Serialization): Message[T] = {
      val headers = Headers()
        .withHeader("versionedId", versionedId.toString)
        .withTtl(expectedConfirmIn)
      Message("state-change", messageId, headers, Payload.fromObject(value))
    }
  }

  case class StateChangeOk(correlationId: Uuid) extends StateChangeResult with OkNullResult[Uuid]

  case class StateChangeOkWithValidationError(correlationId: Uuid, validationError: ValidationError) extends StateChangeResult with OkNullResult[Uuid]

  case class StateChangeNotOk(correlationId: Uuid, ex: Throwable) extends StateChangeResult with NotOkNullResult[Uuid]

  case class DistributionComplete(versionedId: VersionedId, expectedConfirmIn: FiniteDuration, messageId: Uuid = QA.uuid) extends UuidQuestion {
    def toMessage: Message[AnyRef] = {
      val headers = Headers()
        .withHeader("versionedId", versionedId.toString)
        .withTtl(expectedConfirmIn)
      Message("distribution-complete", messageId, headers)
    }
  }

  case class DistributionCompleteOk(correlationId: Uuid) extends DistributionCompleteResult with OkNullResult[Uuid]

  case class DistributionCompleteNotOk(correlationId: Uuid, ex: Throwable) extends DistributionCompleteResult with NotOkNullResult[Uuid]


}

trait SatelliteProtocol[T <: AnyRef] {
  /**
    * distributes state change
    */
  def stateChange(msg: StateChange[T])
                 (implicit executionContext: ExecutionContext, sender: ActorRef)
  : Future[StateChangeResult]

  /**
    * informs that all destinations confirmed
    */

  def distributionComplete(msg: DistributionComplete)
                          (implicit executionContext: ExecutionContext, sender: ActorRef)
  : Future[DistributionCompleteResult]
}
