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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.persistence.AtLeastOnceDelivery
import akka.serialization.Serialization
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonIgnore
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClientExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor._
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.s4s0l.betelgeuse.utils.AllUtils.{listOfFuturesToFutureOfList, _}

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

/**
  * This actually could be generalized to 'broadcast acknowledge able actor'.
  * forwards message to N destinations and awaits its responses confirms received message
  * only if all destinations confirmed.
  *
  * It could also not be an actor, as it does not hold any state...
  *
  * @author Marcin Wielgus
  */
class OriginStateDistributor[T <: AnyRef](settings: Settings[T]) extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Actor.Receive = {
    case OriginStateChanged(deliveryId, versionedId, value, duration) =>
      val originalSender = sender()
      val start = System.currentTimeMillis()
      implicit val timeout: Timeout = duration
      val stateChangeResult: Future[Seq[StateChangeResult]] = listOfFuturesToFutureOfList(
        settings.satelliteStates.map { case (_, api) =>
          val stateChangeRequest = StateChange(versionedId, value.asInstanceOf[T], duration)
          api.stateChange(stateChangeRequest)
            .recover { case ex: Throwable => StateChangeNotOk(stateChangeRequest.messageId, ex) }
        }.toSeq)


      val distributionResult = stateChangeResult.flatMap { results =>
        if (results.exists(_.isNotOk)) {
          //At least one failed so we fail all
          val msgs = results.filter(_.isNotOk).map(_.asInstanceOf[NotOkResult[_, _]])
            .map(_.ex.getMessage).mkString(" AND ")
          Future.successful(
            OriginStateChangedNotOk(deliveryId,
              new Exception(s"Some distributions failed: [$msgs]")))
        } else if (results.exists(_.isInstanceOf[StateChangeOkWithValidationError])) {
          //at least one has validation result we map them and send back
          val allValidationErrors = results.filter(_.isInstanceOf[StateChangeOkWithValidationError])
            .map(_.asInstanceOf[StateChangeOkWithValidationError])
            .flatMap(_.validationError.validationErrors)
          Future.successful(OriginStateChangedOkWithValidationError(deliveryId, versionedId, ValidationError(allValidationErrors)))
        } else {
          //here all is ok so we distribute completion event to all satellites
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
              .map(_ => OriginStateChangedOk(deliveryId, versionedId))
              .recover {
                case _: NoSuchElementException => OriginStateChangedNotOk(deliveryId, new Exception("Some distributions failed"))
                case ex: Throwable => OriginStateChangedNotOk(deliveryId, ex)
              }
          }
        }
      }
      distributionResult.pipeToWithTimeout(originalSender, duration, OriginStateChangedNotOk(deliveryId, new Exception("Timeout...")), context.system.scheduler)
  }
}


object OriginStateDistributor {
  /**
    * Creates an api to be used by origin configuration holder aggregate. Using returned
    * api you can broadcast versioned value to remote services and reliably receive confirmations
    * without tracking state of confirmations from all remote sites individually.
    *
    * @param name     the name must be same at origin and at satellite ends
    * @param services list of remote services to which changes will be distributed
    */
  def startRemote[T <: AnyRef](name: String, services: Seq[BgServiceId])
                              (implicit clientExt: BgClusteringClientExtension,
                               actorRefFactory: ActorRefFactory,
                               simpleSerializer: Serialization)
  : OriginStateDistributor.Protocol[T] = {
    val satellites: Map[String, SatelliteProtocol[T]] = services.map { it =>
      it.systemName -> SatelliteStateActor.getRemote[T](name, it)
    }.toMap
    start(OriginStateDistributor.Settings(s"origin-distributor-$name", satellites))
  }

  /**
    * creates props for actor
    */
  def start[T <: AnyRef](settings: Settings[T], propsMapper: Props => Props = identity)
                        (implicit actorSystem: ActorRefFactory)
  : Protocol[T] = {
    val ref = actorSystem.actorOf(Props(new OriginStateDistributor(settings)))
    new ProtocolImpl[T](ref)
  }

  final case class Settings[T <: AnyRef](name: String, satelliteStates: Map[String, SatelliteProtocol[T]])

  trait Protocol[T] {

    /**
      * Emits state change. Sender should expect
      * [[org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.OriginStateChangedResult]]
      */
    def stateChanged(msg: Protocol.OriginStateChanged[T])
                    (implicit sender: ActorRef = Actor.noSender)
    : Unit

    /**
      * uses deliver method of [[AtLeastOnceDelivery]].
      * Works like [[ProtocolImpl.stateChanged]].
      * Utility to hide actor ref from user of this protocol
      */
    def deliverStateChange(from: AtLeastOnceDelivery, onCall: => Unit = {})
                          (versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
    : Unit
  }

  /**
    * An protocol for [[OriginStateDistributor]]
    */
  private class ProtocolImpl[T](actorRef: => ActorRef) extends Protocol[T] {

    /**
      * Emits state change. Sender should expect
      * [[org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.OriginStateChangedResult]]
      */
    override def stateChanged(msg: Protocol.OriginStateChanged[T])
                             (implicit sender: ActorRef = Actor.noSender)
    : Unit =
      actorRef ! msg

    /**
      * uses deliver method of [[AtLeastOnceDelivery]].
      * Works like [[ProtocolImpl.stateChanged]].
      * Utility to hide actor ref from user of this protocol
      */
    override def deliverStateChange(from: AtLeastOnceDelivery, onCall: => Unit = {})
                                   (versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
    : Unit = {
      from.deliver(actorRef.path)(deliveryId => {
        onCall
        Protocol.OriginStateChanged(deliveryId, versionedId, value, expectedConfirmIn)
      })
    }

  }

  object Protocol {

    /**
      * Converts two protocols with some converter
      */
    def stateDistributorConverter[F, T](distributor: OriginStateDistributor.Protocol[T])(mapper: (VersionedId, F) => T)
    : OriginStateDistributor.Protocol[F] = {
      new OriginStateDistributor.Protocol[F]() {

        override def stateChanged(msg: Protocol.OriginStateChanged[F])(implicit sender: ActorRef): Unit =
          distributor.stateChanged(Protocol.OriginStateChanged(msg.messageId, msg.versionedId, mapper.apply(msg.versionedId, msg.value), msg.expectedConfirmIn))

        override def deliverStateChange(from: AtLeastOnceDelivery, onCall: => Unit = {})(versionedId: VersionedId, value: F, expectedConfirmIn: FiniteDuration): Unit =
          distributor.deliverStateChange(from, onCall)(versionedId, mapper.apply(versionedId, value), expectedConfirmIn)
      }
    }

    sealed trait OriginStateChangedResult extends Result[Long, VersionedId]

    case class OriginStateChanged[T](messageId: Long, versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)
      extends Question[Long]

    case class OriginStateChangedOk(correlationId: Long, value: VersionedId) extends OriginStateChangedResult with OkResult[Long, VersionedId]

    case class OriginStateChangedOkWithValidationError(correlationId: Long, value: VersionedId, errors: ValidationError) extends OriginStateChangedResult with OkResult[Long, VersionedId]

    case class OriginStateChangedNotOk(correlationId: Long, ex: Throwable) extends OriginStateChangedResult with NotOkResult[Long, VersionedId]

    case class ValidationError(validationErrors: Seq[String]) extends JacksonJsonSerializable {
      @JsonIgnore
      def isEmpty: Boolean = validationErrors.isEmpty
    }

    //todo something more fancy?
  }


}