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

package org.s4s0l.betelgeuse.akkacommons.distsharedstate

import akka.actor.Status.{Failure, Status}
import akka.actor.{ActorRef, ActorRefFactory}
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClientExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionistExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.GetCacheValue
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.{Protocol, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.{OriginStateDistributor, SatelliteStateActor}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.GetValueVersion
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

/**
  * @author Marcin Wielgus
  */
object DistributedSharedState {
  /**
    * Creates an api to be used by origin configuration holder aggregate. Using returned
    * api it can broadcast versioned value to remote services and reliably receive confirmations
    * without tracking state.
    *
    * @param name     the name must be same at origin and at satellite ends
    * @param services list of remote services to which changes will be distributed
    */
  def createStateDistributionToRemoteServices[T](name: String, services: Seq[BgServiceId])
                                                (implicit clientExt: BgClusteringClientExtension,
                                                 actorRefFactory: ActorRefFactory): Protocol[T] = {
    val satellites: Map[String, SatelliteStateActor.Protocol[T]] = services.map { it =>
      it.systemName -> SatelliteStateActor.Protocol[T](clientExt.client(it).toActorTarget(SatelliteStateActor.getRemoteName(name)))
    }.toMap
    OriginStateDistributor.start(Settings(s"origin-distributor-$name", satellites))
  }

  /**
    * TODO: actorFinder should be replaced when some common query api for persistence is introduced
    */
  def createSatelliteStateDistribution[V](
                                           name: String,
                                           actorFinder: String => Future[Seq[PersistenceId]])
                                         (implicit
                                          executionContext: ExecutionContext,
                                          receptionistExt: BgClusteringReceptionistExtension,
                                          shardingExt: BgClusteringShardingExtension,
                                          actorRefFactory: ActorRefFactory): SatelliteContext[V] = {
    new SatelliteContext[V](name, actorFinder)
  }

  trait NewVersionedValueListener[R] {
    def newVersionPresent(versionedId: VersionedId, richValue: R): Future[Status]
  }

  class SatelliteContext[V] private[DistributedSharedState](
                                                             name: String,
                                                             actorFinder: String => Future[Seq[PersistenceId]])
                                                           (implicit
                                                            executionContext: ExecutionContext,
                                                            receptionistExt: BgClusteringReceptionistExtension,
                                                            shardingExt: BgClusteringShardingExtension,
                                                            actorRefFactory: ActorRefFactory) {
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
      val listenerX: (VersionedId, V) => Future[Status] = (versionId: VersionedId, _: V) => cache.getValue(versionId).
        flatMap(consumer.newVersionPresent(versionId, _))
        .recover { case ex: Throwable => Failure(ex) }
      addListener(cacheName, listenerX)
      new CachedValueListeningConsumer[R, C](name, cache, consumer, actorFinder)
    }

    def createCache[R](cacheName: String, valueEnricher: V => R, cacheTtl: FiniteDuration): VersionedCache[R] = {
      val keyFactory: VersionedEntityActor.Protocol.GetValue => VersionedId = (it) => it.versionedId
      val valueOwnerFacade: VersionedEntityActor.Protocol.GetValue => Future[Option[V]] =
        (it) => satelliteStateActor.getValue(it).map(x => x.value)
      val cacheAccesor = CacheAccessActor.start[VersionedEntityActor.Protocol.GetValue, VersionedId, R, V](CacheAccessActor.Settings(
        s"sattelite-cache-$name-$cacheName", keyFactory, valueEnricher, valueOwnerFacade, cacheTtl))
      new VersionedCache[R](satelliteStateActor, cacheAccesor)
    }

    def addListener(listenerName: String, onNewVersion: (VersionedId, V) => Future[Status]): Unit = {
      enabled.foreach(_ => throw new Exception("cannot add listeners to enabled context"))
      val listenerObject: DelayedSubsActor.Listener[VersionedId, V] = new DelayedSubsActor.Listener[VersionedId, V] {
        override def onMessage(key: VersionedId, value: V)(implicit sender: ActorRef = ActorRef.noSender): Future[Status] = onNewVersion(key, value)

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

    def notifyStartupValues(implicit executionContext: ExecutionContext): Future[Map[PersistenceId, Failure]] = {
      actorFinder.apply(s"satellite-value-$name")
        .flatMap { idsFound =>
          val listOfFutures = idsFound.map { id =>
            val statusUpdate = for (
              version <- cache.getVersion(id.uniqueId);
              value <- cache.getValue(version);
              status <- consumer.newVersionPresent(version, value)
            ) yield status
            statusUpdate
              .map(status => id -> status)
              .recover { case ex: Throwable => id -> Failure(ex) }
          }
          AllUtils.listOfFuturesToFutureOfList(listOfFutures)
        }.map(_.filter(_._2.isInstanceOf[Failure]).map(x => (x._1, x._2.asInstanceOf[Failure])))
        .map(_.toMap)
    }

  }

  /**
    * TODO: add all other accessors
    */
  class VersionedCache[R] private[distsharedstate](
                                                    versionedEntity: VersionedEntityActor.Protocol[_],
                                                    cacheWrapped: CacheAccessActor.Protocol[VersionedEntityActor.Protocol.GetValue, VersionedId, R]
                                                  )
                                                  (implicit executionContext: ExecutionContext) {
    def getValue(id: VersionedId): Future[R] = {
      cacheWrapped.apply(GetCacheValue(VersionedEntityActor.Protocol.GetValue(id)))
        .map(_.value.left.get.get)
    }

    def getVersion(id: String): Future[VersionedId] = {
      versionedEntity.getVersion(GetValueVersion(id)).map(_.versionedId)
    }


  }

}
