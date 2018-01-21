/*
 * Copyright© 2018 the original author or authors.
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

/*
 * Copyright© 2017 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.pattern.pipe
import akka.persistence.AtLeastOnceDelivery
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStatePublishingActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStatePublishingActor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Events.Event
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.ValueMissingException
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{Uuid, UuidQuestion}
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, QA}

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  *
  * An [[OriginStateActor]] that does not publish every version, but only ones that are explicitly
  * requested to be.
  *
  * @author Marcin Wielgus
  */
class OriginStatePublishingActor[T](settings: Settings[T])
  extends OriginStateActor(OriginStateActor.Settings(settings.name, settings.distributor, settings.stateDistributionRetryInterval))
    with AtLeastOnceDelivery {


  val publishStatus: mutable.Map[VersionedId, Boolean] = mutable.Map()

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case BeforePublishValidationOk(versionedId, messageId) =>
      persist(PublishEvent(messageId, versionedId))(processEvent(false))

    case BeforePublishValidationNotOk(ex, messageId) =>
      sender() ! PublishVersionNotOk(ex, messageId)

    case PublishVersion(versionedId, messageId) =>
      if (getValueAtVersion(versionedId).isDefined) {
        if (publishStatus.contains(versionedId)) {
          sender() ! PublishVersionOk(messageId)
        } else {
          import context.dispatcher
          validatePublication(versionedId)
            .recover { case ex: Throwable => Some(ex) }
            .map {
              case None => BeforePublishValidationOk(versionedId, messageId)
              case Some(ex) => BeforePublishValidationNotOk(ex, messageId)
            }.pipeTo(self)(sender())
        }
      } else {
        sender() ! PublishVersionNotOk(ValueMissingException(versionedId), messageId)
        shardedPassivate()
      }
    case GetPublicationStatus(_, messageId) =>
      if (getCurrentVersionId.version == 0) {
        sender() ! GetPublicationStatusNotOk(ValueMissingException(getCurrentVersionId), messageId)
        shardedPassivate()
      } else {
        sender() ! GetPublicationStatusOk(PublicationStatuses(publishStatus.map(e => PublicationStatus(e._1, e._2)).toList), messageId)
      }
  }

  override def processEvent(recover: Boolean): PartialFunction[Event, Unit] = super.processEvent(recover) orElse {
    case PublishEvent(messageId, versionedId) =>
      publishStatus(versionedId) = false
      distributeStateChange(versionedId, getValueAtVersion(versionedId).get)
      if (!recover)
        sender() ! PublishVersionOk(messageId)
  }


  override protected def distributeStateChanged(versionedId: VersionedId): Unit = {
    super.distributeStateChanged(versionedId)
    publishStatus(versionedId) = true
  }

  /**
    * decides if publication can occur.
    *
    * When called it is guaranteed that value for given version exist
    * and it was never published before.
    *
    * overriding classes may want to validate value in some way before publishing
    *
    * This method can return Some(Exception) to prevent publication or throw an Exception
    */
  protected def validatePublication(versionedId: VersionedId): Future[Option[ValidationException]] = {
    Future.successful(None)
  }

  override protected def valueUpdated(versionedId: VersionedId, value: T): Unit = {
    //we change OriginStateActor behavior not to emit changes on every update but on explicit request
  }

}


object OriginStatePublishingActor {


  def startSharded[T](settings: Settings[T], propsMapper: Props => Props = identity)(implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, Props(new OriginStatePublishingActor(settings)), VersionedEntityActor.entityExtractor orElse entityExtractor)
    Protocol(ref, settings.name)
  }

  def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: PublishVersion => (a.versionedId.id, a)
    case a: GetPublicationStatus => (a.entityId, a)
  }

  sealed trait BeforePublishValidation

  /**
    * An protocol for [[OriginStateActor]]
    */
  class Protocol[T](actorTarget: ActorTarget, shardName: String) extends OriginStateActor.Protocol[T](actorTarget, shardName) {
    /**
      * Publishes given version (if exist) to satellites.
      * If is already published/publishing returns ok without initiating new publication process.
      *
      * @return ok if version is present
      *
      */
    def publish(msg: PublishVersion)
               (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublishVersionResult] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[PublishVersionResult]

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


  private case class PublishEvent(messageId: String, versionedId: VersionedId) extends Event

  final case class Settings[T](name: String, distributor: OriginStateDistributor.StateDistributorProtocol[T], stateDistributionRetryInterval: FiniteDuration = 30 seconds)

  private case class BeforePublishValidationOk(versionedId: VersionedId, messageId: Uuid) extends BeforePublishValidation

  private case class BeforePublishValidationNotOk(ex: Throwable, messageId: Uuid) extends BeforePublishValidation

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: ActorTarget, shardName: String): Protocol[T] = new Protocol(actorRef, shardName)

    sealed trait PublishVersionResult extends QA.NullResult[Uuid]

    sealed trait GetPublicationStatusResult extends QA.Result[Uuid, PublicationStatuses]

    case class PublishVersion(versionedId: VersionedId, messageId: Uuid = QA.uuid) extends UuidQuestion

    case class PublishVersionOk(correlationId: Uuid) extends PublishVersionResult with QA.OkNullResult[Uuid]

    case class PublishVersionNotOk(ex: Throwable, correlationId: Uuid) extends PublishVersionResult with QA.NotOkNullResult[Uuid]

    //TODO asking for publication status could be moved to [[OriginStateActor]]
    case class GetPublicationStatus(entityId: String, messageId: Uuid = QA.uuid) extends UuidQuestion

    case class PublicationStatus(versionedId: VersionedId, completed: Boolean)

    case class GetPublicationStatusOk(value: PublicationStatuses, correlationId: Uuid) extends GetPublicationStatusResult with QA.OkResult[Uuid, PublicationStatuses]

    case class GetPublicationStatusNotOk(ex: Throwable, correlationId: Uuid) extends GetPublicationStatusResult with QA.NotOkResult[Uuid, PublicationStatuses]

    case class PublicationStatuses(statuses: List[PublicationStatus])

  }

  class ValidationException(msg: String) extends Exception(msg)

}
