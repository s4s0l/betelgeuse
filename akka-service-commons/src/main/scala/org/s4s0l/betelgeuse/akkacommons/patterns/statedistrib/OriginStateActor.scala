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

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.Props
import akka.persistence.AtLeastOnceDelivery
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateActor.{ConfirmEvent, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Events.Event
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
//todo missing test
/**
  * Actor that is a versioned entity that can handle state distribution
  * via [[OriginStateDistributor]].
  *
  * @author Marcin Wielgus
  */
class OriginStateActor[T](settings: Settings[T])
  extends VersionedEntityActor[T](VersionedEntityActor.Settings(settings.name))
    with AtLeastOnceDelivery {

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case OriginStateDistributor.StateDistributorProtocol.OriginStateChangedOk(deliveryId, versionedId) =>
      persist(ConfirmEvent(deliveryId, versionedId))(processEvent(false))
    case OriginStateDistributor.StateDistributorProtocol.OriginStateChangedNotOk(_, ex) =>
      log.error(ex, "Undelivered state distribution")
  }

  override def processEvent(recover: Boolean): PartialFunction[Event, Unit] = super.processEvent(recover) orElse {
    case ConfirmEvent(deliveryId, versionedId) =>
      confirmDelivery(deliveryId)
      distributeStateChanged(versionedId)
  }

  override protected def valueUpdated(versionedId: VersionedId, value: T): Unit = {
    distributeStateChange(versionedId, value.asInstanceOf[T])
  }

  protected def distributeStateChanged(versionedId: VersionedId): Unit = {}

  protected def distributeStateChange(versionedId: VersionedId, value: T): Unit = settings.distributor.deliverStateChange(this)(versionedId, value, redeliverInterval)

  override def redeliverInterval: FiniteDuration = settings.stateDistributionRetryInterval

}


object OriginStateActor {

  def startSharded[T](settings: Settings[T], propsMapper: Props => Props = identity)(implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, Props(new OriginStateActor(settings)), VersionedEntityActor.entityExtractor)
    Protocol(ref, settings.name)
  }


  final case class Settings[T](name: String, distributor: OriginStateDistributor.StateDistributorProtocol[T], stateDistributionRetryInterval: FiniteDuration = 30 seconds)

  /**
    * An protocol for [[OriginStateActor]]
    */
  class Protocol[T] protected(actorTarget: ActorTarget, shardName: String) extends VersionedEntityActor.Protocol[T](actorTarget, shardName) {

  }

  private case class ConfirmEvent(deliveryId: Long, versionedId: VersionedId) extends Event

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: ActorTarget, shardName: String): Protocol[T] = new Protocol(actorRef, shardName)


  }


}    