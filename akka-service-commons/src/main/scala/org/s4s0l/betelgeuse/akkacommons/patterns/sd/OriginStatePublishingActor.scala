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
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStatePublishingActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStatePublishingActor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Events.Event
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.ValueMissingException
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{Uuid, UuidQuestion}
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, QA}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  *
  * An [[OriginStateActor]] that does not publish every version, but only ones that are explicitly
  * requested to be.
  *
  * @author Marcin Wielgus
  */
class OriginStatePublishingActor[T](settings: OriginStateActor.Settings[T])
  extends OriginStateActor(settings)
    with AtLeastOnceDelivery {

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case PublishVersion(versionedId, messageId) =>
      if (getValueAtVersion(versionedId).isDefined) {
        if (publishStatus.contains(versionedId)) {
          sender() ! PublishVersionOk(messageId)
        } else {
          persist(PublishEvent(messageId, versionedId))(processEvent(false))
        }
      } else {
        sender() ! PublishVersionNotOk(ValueMissingException(versionedId), messageId)
        shardedPassivate()
      }

  }

  override def processEvent(recover: Boolean): PartialFunction[Event, Unit] = super.processEvent(recover) orElse {
    case PublishEvent(messageId, versionedId) =>
      distributeStateChange(versionedId, getValueAtVersion(versionedId).get)
      if (!recover)
        sender() ! PublishVersionOk(messageId)
  }


  override protected def valueUpdated(versionedId: VersionedId, value: T): Unit = {
    //we change OriginStateActor behavior not to emit changes on every update but on explicit request
  }

}


object OriginStatePublishingActor {


  def startSharded[T](settings: OriginStateActor.Settings[T], propsMapper: Props => Props = identity)
                     (implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, Props(new OriginStatePublishingActor(settings)),
      entityExtractor)
    Protocol(ref, settings.name)
  }

  def entityExtractor: ShardRegion.ExtractEntityId = OriginStateActor.entityExtractor orElse {
    case a: PublishVersion => (a.versionedId.id, a)
  }

  sealed trait BeforePublishValidation

  /**
    * An protocol for [[OriginStateActor]]
    */
  class Protocol[T](actorTarget: ActorTarget, shardName: String)
    extends OriginStateActor.Protocol[T](actorTarget, shardName) {
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


  }

  private case class PublishEvent(messageId: String, versionedId: VersionedId) extends Event

  private case class BeforePublishValidationOk(versionedId: VersionedId, messageId: Uuid) extends BeforePublishValidation

  private case class BeforePublishValidationNotOk(ex: Throwable, messageId: Uuid) extends BeforePublishValidation

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: ActorTarget, shardName: String): Protocol[T] = new Protocol(actorRef, shardName)

    sealed trait PublishVersionResult extends QA.NullResult[Uuid]


    case class PublishVersion(versionedId: VersionedId, messageId: Uuid = QA.uuid) extends UuidQuestion

    case class PublishVersionOk(correlationId: Uuid) extends PublishVersionResult with QA.OkNullResult[Uuid]

    case class PublishVersionNotOk(ex: Throwable, correlationId: Uuid) extends PublishVersionResult with QA.NotOkNullResult[Uuid]

  }

}
