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

import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.AtLeastOnceDelivery
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateActor.{ConfirmEvent, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.ValidationError
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Events.Event
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.ValueMissingException
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{Uuid, UuidQuestion}
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, QA}

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
//todo missing test
/**
  * Actor that is a versioned entity that can handle state distribution
  * via [[OriginStateDistributor]].
  *
  * @author Marcin Wielgus
  */
class OriginStateActor[T](settings: Settings[T])
  extends VersionedEntityActor[T](VersionedEntityActor.Settings[T](settings.name, settings.validator))
    with AtLeastOnceDelivery {

  /**
    * false - send no response yet
    * true - got ok response
    * validation errors - confirmed but with some errors
    */
  protected val publishStatus: mutable.Map[VersionedId, Either[Boolean, ValidationError]] = mutable.Map()

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case OriginStateDistributor.Protocol.OriginStateChangedOk(deliveryId, versionedId) =>
      persist(ConfirmEvent(deliveryId, versionedId, None))(processEvent(false))
    case OriginStateDistributor.Protocol.OriginStateChangedOkWithValidationError(deliveryId, versionedId, errs) =>
      persist(ConfirmEvent(deliveryId, versionedId, Some(errs)))(processEvent(false))
    case OriginStateDistributor.Protocol.OriginStateChangedNotOk(_, ex) =>
      log.error(ex, "Undelivered state distribution")
    case GetPublicationStatus(_, messageId) =>
      if (getCurrentVersionId.version == 0) {
        sender() ! GetPublicationStatusNotOk(ValueMissingException(getCurrentVersionId), messageId)
        shardedPassivate()
      } else {
        sender() ! GetPublicationStatusOk(PublicationStatuses(publishStatus.map(e => {
          val completed = e._2.isRight || (e._2.isLeft && e._2.left.get)
          PublicationStatus(e._1, completed, e._2.right.getOrElse(ValidationError(Seq())))
        }).toList), messageId)
      }
  }

  override def processEvent(recover: Boolean): PartialFunction[Event, Unit] = super.processEvent(recover) orElse {
    case ConfirmEvent(deliveryId, versionedId, errors) =>
      confirmDelivery(deliveryId)
      distributeStateChanged(versionedId, errors)
  }

  protected def distributeStateChanged(versionedId: VersionedId, errors: Option[ValidationError]): Unit = {
    errors match {
      case None =>
        publishStatus(versionedId) = Left(true)
      case Some(err) =>
        publishStatus(versionedId) = Right(err)
    }
  }

  override protected def valueUpdated(versionedId: VersionedId, value: T): Unit = {
    distributeStateChange(versionedId, value.asInstanceOf[T])
  }

  protected def distributeStateChange(versionedId: VersionedId, value: T): Unit = {
    settings.distributor.deliverStateChange(this)(versionedId, value, redeliverInterval)
    publishStatus(versionedId) = Left(false)
  }

  override def redeliverInterval: FiniteDuration = settings.stateDistributionRetryInterval

}


object OriginStateActor {

  def startSharded[T](settings: Settings[T], propsMapper: Props => Props = identity)(implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, Props(new OriginStateActor(settings)), entityExtractor)
    Protocol(ref, settings.name)
  }

  def entityExtractor: ShardRegion.ExtractEntityId = VersionedEntityActor.entityExtractor orElse {
    case a: GetPublicationStatus => (a.entityId, a)
  }

  final case class Settings[T](name: String,
                               distributor: OriginStateDistributor.Protocol[T],
                               validator: (VersionedId, T) => T = (_: VersionedId, a: T) => a,
                               stateDistributionRetryInterval: FiniteDuration = 30 seconds)

  /**
    * An protocol for [[OriginStateActor]]
    */
  class Protocol[T] protected(actorTarget: ActorTarget, shardName: String)
    extends VersionedEntityActor.Protocol[T](actorTarget, shardName) {

    /**
      * gets current publication statuses, result contains list of all statuses of all
      * published versions
      *
      */
    def publishStatus(msg: GetPublicationStatus)
                     (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[GetPublicationStatusResult] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[GetPublicationStatusResult]

  }

  private case class ConfirmEvent(deliveryId: Long, versionedId: VersionedId, option: Option[ValidationError]) extends Event

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: ActorTarget, shardName: String): Protocol[T] = new Protocol(actorRef, shardName)

    sealed trait GetPublicationStatusResult extends QA.Result[Uuid, PublicationStatuses]

    case class GetPublicationStatus(entityId: String, messageId: Uuid = QA.uuid) extends UuidQuestion

    case class PublicationStatus(versionedId: VersionedId, completed: Boolean, validationError: ValidationError)

    case class PublicationStatuses(statuses: List[PublicationStatus])

    case class GetPublicationStatusOk(value: PublicationStatuses, correlationId: Uuid) extends GetPublicationStatusResult with QA.OkResult[Uuid, PublicationStatuses]

    case class GetPublicationStatusNotOk(ex: Throwable, correlationId: Uuid) extends GetPublicationStatusResult with QA.NotOkResult[Uuid, PublicationStatuses]


  }

}