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

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.persistence.AtLeastOnceDelivery
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.Headers
import org.s4s0l.betelgeuse.akkacommons.patterns.message.{Message, Payload}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.{OriginStateChanged, OriginStateChangedNotOk, OriginStateChangedOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.serialization.SimpleSerializer
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.s4s0l.betelgeuse.utils.AllUtils.{listOfFuturesToFutureOfList, _}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * This actually could be generalized to 'broadcast acknowledgeable actor'.
  * forwards message to N destinations and awaits its responses confirms received message
  * only if all destinations confirmed.
  *
  * @author Marcin Wielgus
  */
class OriginStateDistributor[T](settings: Settings[T]) extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Actor.Receive = {
    case OriginStateChanged(deliveryId, versionedId, value, duration) =>
      val originalSender = sender()
      val start = System.currentTimeMillis()
      implicit val timeout: Timeout = duration
      val stateChangeResult: Future[String] = listOfFuturesToFutureOfList(
        settings.satelliteStates.map { case (_, api) =>
          val stateChangeRequest = StateChange(versionedId, value.asInstanceOf[T], duration)
          api.stateChange(stateChangeRequest)
            .recover { case ex: Throwable => StateChangeNotOk(stateChangeRequest.messageId, ex) }
        }.toSeq)
        .filter(seq => !seq.exists(_.isNotOk))
        .map(_ => "ok")
      stateChangeResult.flatMap { _ =>
        val timeSpentSoFar = System.currentTimeMillis() - start
        if (timeSpentSoFar > duration.toMillis) {
          Future.failed(new Exception("state distribution took too long, aborting commit."))
        } else {
          implicit val timeout: Timeout = duration - (timeSpentSoFar millisecond)
          listOfFuturesToFutureOfList(
            settings.satelliteStates.map { case (_, api) =>
              val distributionComplete = DistributionComplete(versionedId, timeout.duration)
              api.distributionComplete(distributionComplete)
                .recover { case ex: Throwable => DistributionCompleteNotOk(distributionComplete.messageId, ex) }
            }.toSeq)
            .filter(seq => !seq.exists(_.isNotOk))
        }
      }
        .map(_ => OriginStateChangedOk(deliveryId))
        .recover {
          case _: NoSuchElementException => OriginStateChangedNotOk(deliveryId, new Exception("Some distributions failed"))
          case ex: Throwable => OriginStateChangedNotOk(deliveryId, ex)
        }
        .pipeToWithTimeout(originalSender, duration, OriginStateChangedNotOk(deliveryId, new Exception("Timeout...")), context.system.scheduler)
  }
}


object OriginStateDistributor {
  /**
    * creates props for actor
    */
  def start[T](settings: Settings[T], propsMapper: Props => Props = identity)
              (implicit actorSystem: ActorRefFactory)
  : Protocol[T] = {
    val ref = actorSystem.actorOf(Props(new OriginStateDistributor(settings)))
    Protocol(ref)
  }

  /**
    */
  trait SatelliteProtocol[T] {
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

  /**
    * Adapts satellite protocol for remote message passing via [[org.s4s0l.betelgeuse.akkacommons.patterns.message.Message]] pattern
    *
    * @param actorTarget      actor to ask, should respond with Messages also
    * @param simpleSerializer serializer to be used for marshalling T
    */
  class RemoteSatelliteProtocol[T](actorTarget: ActorTarget)(implicit simpleSerializer: SimpleSerializer)
    extends SatelliteProtocol[T] {
    /**
      * distributes state change
      */
    override def stateChange(stateChangeMessage: StateChange[T])(implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[StateChangeResult] = {
      actorTarget.?(stateChangeMessage.toMessage)(stateChangeMessage.expectedConfirmIn, sender).map {
        case msg@Message("state-change-ok", _, _, _) =>
          StateChangeOk(msg.correlationId)
        case msg@Message("state-change-not-ok", _, _, _) =>
          StateChangeNotOk(msg.correlationId, new Exception(s"Remote satelliteError: ${msg.failedOpt.getOrElse(-1)}, message was: ${msg.payload.asString}"))
        case _ =>
          StateChangeNotOk(stateChangeMessage.messageId, new Exception(s"Remote satellite unknown response error."))
      }
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
      }
    }
  }

  final case class Settings[T](name: String, satelliteStates: Map[String, SatelliteProtocol[T]])

  /**
    * An protocol for [[OriginStateDistributor]]
    */
  final class Protocol[T] private(actorRef: => ActorRef) {

    /**
      * Emits state change. Sender should expect
      * [[org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.OriginStateChangedResult]]
      */
    def stateChanged(msg: OriginStateChanged[T])
                    (implicit sender: ActorRef = Actor.noSender)
    : Unit =
      actorRef ! msg

    /**
      * uses deliver method of [[AtLeastOnceDelivery]].
      * Works like [[Protocol.stateChanged]].
      * Utility to hide actor ref from user of this protocol
      */
    def deliverStateChange(from: AtLeastOnceDelivery)
                          (versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
    : Unit = {
      from.deliver(actorRef.path)(deliveryId => OriginStateChanged(deliveryId, versionedId, value, expectedConfirmIn))
    }

  }

  object SatelliteProtocol {


    sealed trait DistributionCompleteResult extends Result[Uuid, Null]

    sealed trait StateChangeResult extends Result[Uuid, Null]

    case class StateChange[T](versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration) extends UuidQuestion {
      def toMessage(implicit simpleSerializer: SimpleSerializer): Message = {
        val headers = Headers()
          .withHeader("versionedId", versionedId.toString)
          .withTtl(expectedConfirmIn)
        Message("state-change", messageId, headers, Payload.apply(value.asInstanceOf[AnyRef]))
      }
    }

    case class StateChangeOk(correlationId: Uuid) extends StateChangeResult with OkNullResult[Uuid]

    case class StateChangeNotOk(correlationId: Uuid, ex: Throwable) extends StateChangeResult with NotOkNullResult[Uuid]

    case class DistributionComplete(versionedId: VersionedId, expectedConfirmIn: FiniteDuration) extends UuidQuestion {
      def toMessage: Message = {
        val headers = Headers()
          .withHeader("versionedId", versionedId.toString)
          .withTtl(expectedConfirmIn)
        Message("distribution-complete", messageId, headers, "")
      }
    }

    case class DistributionCompleteOk(correlationId: Uuid) extends DistributionCompleteResult with OkNullResult[Uuid]

    case class DistributionCompleteNotOk(correlationId: Uuid, ex: Throwable) extends DistributionCompleteResult with NotOkNullResult[Uuid]

  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: => ActorRef)
    : Protocol[T] =
      new Protocol(actorRef)

    case class OriginStateChanged[T](messageId: Long, versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
      extends Question[Long]

    sealed trait OriginStateChangedResult extends NullResult[Long]

    case class OriginStateChangedOk(correlationId: Long) extends OriginStateChangedResult with OkNullResult[Long]

    case class OriginStateChangedNotOk(correlationId: Long, ex: Throwable) extends OriginStateChangedResult with NotOkNullResult[Long]

  }


}