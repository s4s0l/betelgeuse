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

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.persistence.AtLeastOnceDelivery
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.{ConfirmNotOk, ConfirmOk, OriginStateChanged}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.s4s0l.betelgeuse.utils.AllUtils.{listOfFuturesToFutureOfList, _}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * This actually could be generalized to broadcast acknowledgeable actor.
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
          api.stateChanged(stateChangeRequest)
            .recover { case ex: Throwable => ChangeNotOk(stateChangeRequest.messageId, ex) }
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
              api.stateDistributed(distributionComplete)
                .recover { case ex: Throwable => DistributionNotOk(distributionComplete.messageId, ex) }
            }.toSeq)
            .filter(seq => !seq.exists(_.isNotOk))
        }
      }
        .map(_ => ConfirmOk(deliveryId))
        .recover {
          case ex: NoSuchElementException => ConfirmNotOk(deliveryId, new Exception("Some distributions failed"))
          case ex: Throwable => ConfirmNotOk(deliveryId, ex)
        }
        .pipeToWithTimeout(originalSender, duration, ConfirmNotOk(deliveryId, new Exception("Timeout...")), context.system.scheduler)
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
    def stateChanged(msg: StateChange[T])
                    (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[StateChangeResult]

    /**
      * informs that all destinations confirmed
      */

    def stateDistributed(msg: DistributionComplete)
                        (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[DistributionResult]
  }

  /**
    * An protocol for [[OriginStateDistributor]]
    */
  final class Protocol[T] private(actorRef: => ActorRef) {

    /**
      * Emits state change. Sender should expect
      * [[org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.OriginStateChangedConfirm]]
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
    def deliver(from: AtLeastOnceDelivery)
               (versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
    : Unit = {
      from.deliver(actorRef.path)(deliveryId => OriginStateChanged(deliveryId, versionedId, value, expectedConfirmIn))
    }

  }

  final case class Settings[T](name: String, satelliteStates: Map[String, SatelliteProtocol[T]])

  object SatelliteProtocol {

    sealed trait StateChangeResult extends Result[Uuid, Null]

    sealed trait DistributionResult extends Result[Uuid, Null]

    case class StateChange[T](versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration) extends UuidQuestion

    case class ChangeOk(correlationId: Uuid) extends StateChangeResult with OkNullResult[Uuid]

    case class ChangeNotOk(correlationId: Uuid, ex: Throwable) extends StateChangeResult with NotOkNullResult[Uuid]

    case class DistributionComplete(versionedId: VersionedId, expectedConfirmIn: FiniteDuration) extends UuidQuestion

    case class DistributionOk(correlationId: Uuid) extends DistributionResult with OkNullResult[Uuid]

    case class DistributionNotOk(correlationId: Uuid, ex: Throwable) extends DistributionResult with NotOkNullResult[Uuid]

  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: => ActorRef)
    : Protocol[T] =
      new Protocol(actorRef)

    sealed trait OriginStateChangedConfirm extends NullResult[Long]

    case class OriginStateChanged[T](messageId: Long, versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
      extends Question[Long]

    case class ConfirmOk(correlationId: Long) extends OriginStateChangedConfirm with OkNullResult[Long]

    case class ConfirmNotOk(correlationId: Long, ex: Throwable) extends OriginStateChangedConfirm with NotOkNullResult[Long]

  }


}    