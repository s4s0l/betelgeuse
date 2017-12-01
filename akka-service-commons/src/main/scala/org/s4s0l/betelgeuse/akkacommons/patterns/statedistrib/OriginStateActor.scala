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

import akka.actor.Props
import akka.persistence.AtLeastOnceDelivery
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateActor.{Confirm, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

/**
  * Actor that is a versioned entity that can handle state distribution
  * via [[OriginStateDistributor]].
  *
  * @author Marcin Wielgus
  */
class OriginStateActor[T](settings: Settings[T])
  extends VersionedEntityActor(VersionedEntityActor.Settings(settings.name))
    with AtLeastOnceDelivery {

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case OriginStateDistributor.Protocol.OriginStateChangedConfirm(deliveryId, _) =>
      persist(Confirm(deliveryId))(processEvent(false))
  }

  override def processEvent(recover: Boolean): PartialFunction[Any, Unit] = super.processEvent(recover) orElse {
    case Confirm(deliveryId) => confirmDelivery(deliveryId)
  }

  override protected def valueUpdated(versionedId: VersionedId, value: Any): Unit = {
    settings.distributor.deliver(this)(versionedId, value.asInstanceOf[T], redeliverInterval)
  }

  override def redeliverInterval: FiniteDuration = settings.stateDistributionRetryInterval

}


object OriginStateActor {

  def startSharded[T](settings: Settings[T], propsMapper: Props => Props = identity)(implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, Props(new OriginStateActor(settings)), VersionedEntityActor.entityExtractor)
    Protocol(ref)
  }


  final case class Settings[T](name: String, distributor: OriginStateDistributor.Protocol[T], stateDistributionRetryInterval: FiniteDuration = 30 seconds)

  /**
    * An protocol for [[OriginStateActor]]
    */
  class Protocol[T] private(actorTarget: ActorTarget) extends VersionedEntityActor.Protocol[T](actorTarget) {

  }

  private case class Confirm(deliveryId: Long)

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: ActorTarget): Protocol[T] = new Protocol(actorRef)


  }


}    