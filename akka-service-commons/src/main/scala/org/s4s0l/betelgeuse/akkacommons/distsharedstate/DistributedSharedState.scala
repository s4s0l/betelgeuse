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

package org.s4s0l.betelgeuse.akkacommons.distsharedstate

import akka.actor.{ActorRef, ActorRefFactory}
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClientExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionistExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedState.NewVersionedValueListener.NewVersionResult
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor.Protocol.{Publish, PublishNotOk, PublishOk, PublishResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.GetCacheValue
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.ValueOwnerFacade
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.ValueOwnerFacade.{OwnerValueNotOk, OwnerValueOk, OwnerValueResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.{RemoteSatelliteProtocol, SatelliteProtocol, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.{OriginStateDistributor, SatelliteStateActor}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.{GetValueVersion, ValueNotOk, ValueOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.akkacommons.serialization.SimpleSerializer
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{NotOkNullResult, NullResult, OkNullResult}
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.Success

/**
  * Creates mechanism of reliable distribution of some versioned entity to remote services,
  * which can be accessed on remote service through cache.
  *
  * On remote services use [[DistributedSharedState.createSatelliteStateDistribution]]
  * on source service [[DistributedSharedState.createStateDistributionToRemoteServices]] in combination with
  * some actor that actually holds state, for example [[org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateActor]].
  *
  * Origin sends distribution request through [[OriginStateDistributor]] which sends this state
  * to all remote [[SatelliteStateActor]]s when all of them confirm reception to distributor
  * distributor sends second message (DistributionComplete) to all  [[SatelliteStateActor]]s so
  * they will know that state is distributed to all remote sites. This Message is also confirmed
  * back to distributor, when all remote actors confirm reception of DistributionComplete message
  * distributor confirms delivery of distribution request to Origin.
  *
  * On the satellite side when DistributionComplete is received an listeners can be triggered.
  * Each added listener will get an reference to cache that can be used to access value stored in
  * [[SatelliteStateActor]]. This value can be also preprocessed before it populates cache.
  *
  * Cache is local to VM, but [[SatelliteStateActor]] is sharded.
  *
  * @author Marcin Wielgus
  */
object DistributedSharedState {
  /**
    * Creates an api to be used by origin configuration holder aggregate. Using returned
    * api it can broadcast versioned value to remote services and reliably receive confirmations
    * without tracking state of confirmations from all remote sites.
    *
    * @param name     the name must be same at origin and at satellite ends
    * @param services list of remote services to which changes will be distributed
    */
  def createStateDistributionToRemoteServices[T](name: String, services: Seq[BgServiceId])
                                                (implicit clientExt: BgClusteringClientExtension, actorRefFactory: ActorRefFactory, simpleSerializer: SimpleSerializer)
  : OriginStateDistributor.Protocol[T] = {
    val satellites: Map[String, SatelliteProtocol[T]] = services.map { it =>
      it.systemName -> new RemoteSatelliteProtocol[T](clientExt.client(it).toActorTarget(SatelliteStateActor.getRemoteName(name)))
    }.toMap
    OriginStateDistributor.start(Settings(s"origin-distributor-$name", satellites))
  }

  /**
    * TODO: actorFinder should be replaced when some common query api for persistence is introduced
    */
  def createSatelliteStateDistribution[V](name: String,
                                          actorFinder: String => Future[Seq[PersistenceId]])
                                         (implicit
                                          receptionistExt: BgClusteringReceptionistExtension,
                                          shardingExt: BgClusteringShardingExtension,
                                          actorRefFactory: ActorRefFactory,
                                          classTag: ClassTag[V])
  : SatelliteContext[V] = {
    new SatelliteContext[V](name, actorFinder)
  }

  trait NewVersionedValueListener[R] {
    def onNewVersionAsk(versionedId: VersionedId, aValue: R)
                       (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[NewVersionResult]
  }

  trait OnNewVersionListener {

  }

  class SatelliteContext[V] private[DistributedSharedState](name: String,
                                                            actorFinder: String => Future[Seq[PersistenceId]])
                                                           (implicit
                                                            receptionistExt: BgClusteringReceptionistExtension,
                                                            shardingExt: BgClusteringShardingExtension,
                                                            actorRefFactory: ActorRefFactory,
                                                            classTag: ClassTag[V]) {
    private val listeners = Promise[Seq[DelayedSubsActor.Listener[VersionedId, V]]]()
    private val pubSub = DelayedSubsActor.start(DelayedSubsActor.Settings(s"satellite-listener-$name", listeners.future))
    private val satelliteStateActor: SatelliteStateActor.Protocol[V] = SatelliteStateActor.startSharded(
      SatelliteStateActor.Settings(name, pubSub), receptionist = Some(receptionistExt))
    private var listenersSoFar: List[DelayedSubsActor.Listener[VersionedId, V]] = List()
    private var enabled: Option[Boolean] = None

    def createCachedValueListeningConsumer[R, C <: NewVersionedValueListener[R]](
                                                                                  cacheName: String,
                                                                                  valueEnricher: V => R,
                                                                                  cacheTtl: FiniteDuration,
                                                                                  consumerFactory: VersionedCache[R] => C)
    : CachedValueListeningConsumer[R, C] = {
      enabled.foreach(_ => throw new Exception("cannot add listeners to enabled context"))
      val cache: VersionedCache[R] = createCache(cacheName, valueEnricher, cacheTtl)
      val consumer: C = consumerFactory(cache)
      val VRMappingListener = new NewVersionedValueListener[V] {
        override def onNewVersionAsk(versionedId: VersionedId, aValue: V)
                                    (implicit executionContext: ExecutionContext, sender: ActorRef)
        : Future[NewVersionResult] = {
          cache.getValue(versionedId)
            .flatMap(consumer.onNewVersionAsk(versionedId, _))
            .map {
              case NewVersionedValueListener.NewVersionOk(_) => NewVersionedValueListener.NewVersionOk(versionedId)
              case NewVersionedValueListener.NewVersionNotOk(_, ex) => NewVersionedValueListener.NewVersionNotOk(versionedId, ex)
            }
            .recover { case ex: Throwable => NewVersionedValueListener.NewVersionNotOk(versionedId, ex) }
        }
      }
      addListener(cacheName, VRMappingListener)
      new CachedValueListeningConsumer[R, C](name, cache, consumer, actorFinder)
    }

    def createCache[R](cacheName: String, valueEnricher: V => R, cacheTtl: FiniteDuration): VersionedCache[R] = {
      val keyFactory: VersionedEntityActor.Protocol.GetValue => VersionedId = (it) => it.messageId
      val valueOwnerFacade = new ValueOwnerFacade[VersionedEntityActor.Protocol.GetValue, VersionedId, V] {
        override def apply(getterMessage: VersionedEntityActor.Protocol.GetValue)
                          (implicit executionContext: ExecutionContext, sender: ActorRef)
        : Future[OwnerValueResult[VersionedId, V]] = {
          satelliteStateActor.getValue(getterMessage).map {
            case ValueOk(_, x) => OwnerValueOk(keyFactory.apply(getterMessage), x)
            case ValueNotOk(_, ex) => OwnerValueNotOk(keyFactory.apply(getterMessage), ex)
          }
        }
      }
      val cacheAccessor = CacheAccessActor.start[VersionedEntityActor.Protocol.GetValue, VersionedId, R, V](CacheAccessActor.Settings(
        s"satellite-cache-$name-$cacheName", keyFactory, valueEnricher, valueOwnerFacade, cacheTtl))
      new VersionedCache[R](satelliteStateActor, cacheAccessor)
    }

    def addListener(listenerName: String, onNewVersion: NewVersionedValueListener[V]): Unit = {
      enabled.foreach(_ => throw new Exception("cannot add listeners to enabled context"))
      val listenerObject: DelayedSubsActor.Listener[VersionedId, V] = new DelayedSubsActor.Listener[VersionedId, V] {
        override def publish(publishMessage: Publish[VersionedId, V])
                            (implicit executionContext: ExecutionContext, sender: ActorRef):
        Future[PublishResult[VersionedId]] = onNewVersion.onNewVersionAsk(publishMessage.messageId, publishMessage.payload).map {
          case NewVersionedValueListener.NewVersionOk(_) => PublishOk(publishMessage.messageId)
          case NewVersionedValueListener.NewVersionNotOk(_, ex) => PublishNotOk(publishMessage.messageId, ex)
        }

        override def name: String = listenerName
      }
      listenersSoFar = listenerObject :: listenersSoFar
    }

    def enable(): Unit = {
      enabled.foreach(_ => throw new Exception("Already enabled"))

      listeners.complete(Success(listenersSoFar))
      enabled = Some(true)
    }


  }

  class CachedValueListeningConsumer[R, C <: NewVersionedValueListener[R]] private[distsharedstate](
                                                                                                     private val name: String,
                                                                                                     val cache: VersionedCache[R],
                                                                                                     val consumer: C,
                                                                                                     private val actorFinder: String => Future[Seq[PersistenceId]]) {

    def notifyStartupValues(implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender): Future[Map[PersistenceId, Throwable]] = {
      actorFinder.apply(s"satellite-value-$name")
        .flatMap { idsFound =>
          val listOfFutures = idsFound.map { id =>
            val statusUpdate = for (
              version <- cache.getVersion(id.uniqueId);
              value <- cache.getValue(version);
              status <- consumer.onNewVersionAsk(version, value)
            ) yield status
            statusUpdate
              .map(status => id -> status)
              .recover { case ex: Throwable => id -> NewVersionedValueListener.NewVersionNotOk(VersionedId("", -1), ex) }
          }
          AllUtils.listOfFuturesToFutureOfList(listOfFutures)
        }.map(_.filter(_._2.isNotOk).map(x => (x._1, x._2.asInstanceOf[NewVersionedValueListener.NewVersionNotOk].ex)))
        .map(_.toMap)
    }

  }

  /**
    * TODO: add all other accessors
    */
  class VersionedCache[R] private[distsharedstate](
                                                    versionedEntity: VersionedEntityActor.Protocol[_],
                                                    cacheWrapped: CacheAccessActor.Protocol[VersionedEntityActor.Protocol.GetValue, VersionedId, R]
                                                  ) {
    def getValue(id: VersionedId)
                (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[R] = {
      cacheWrapped.apply(GetCacheValue(VersionedEntityActor.Protocol.GetValue(id)))
        .map {
          case CacheAccessActor.Protocol.GetCacheValueOk(_, value) => value
          case CacheAccessActor.Protocol.GetCacheValueNotOk(_, ex) => throw ex
        }
    }

    def getVersion(id: String)
                  (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[VersionedId] = {
      versionedEntity.getVersion(GetValueVersion(id)).map(_.value)
    }


  }

  object NewVersionedValueListener {

    sealed trait NewVersionResult extends NullResult[VersionedId]

    case class NewVersionOk(correlationId: VersionedId) extends NewVersionResult with OkNullResult[VersionedId]

    case class NewVersionNotOk(correlationId: VersionedId, ex: Throwable) extends NewVersionResult with NotOkNullResult[VersionedId]

  }

}
