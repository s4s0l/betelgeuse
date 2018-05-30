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

package org.s4s0l.betelgeuse.akkacommons.distsharedstate

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionistExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.NewVersionedValueListener.NewVersionNotOk
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor.Protocol.{Publish, PublishNotOk, PublishOk, PublishResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.GetCacheValue
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.ValueOwnerFacade
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.ValueOwnerFacade.{OwnerValueNotOk, OwnerValueOk, OwnerValueResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.{OriginStateDistributor, SatelliteStateActor, SatelliteValueHandler}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JournalReader, PersistenceId}
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.Success

/**
  * Creates mechanism of reliable distribution of some versioned entity to remote services,
  * which can be accessed on remote service through cache.
  *
  * On remote services use [[BgSatelliteStateService.createSatelliteStateFactory]]
  * on source service [[BgOriginStateService.createRemoteDistribution]] in combination with
  * some actor that actually holds state, for example [[BgOriginStateService.createOriginState]].
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
  * //SAMPLE!!!!!
  *
  * @author Marcin Wielgus
  */
object DistributedSharedState {


  /**
    *
    */
  def createSatelliteStateDistribution[I, V](name: String, handler: SatelliteValueHandler[I, V])
                                            (implicit
                                             receptionistExt: BgClusteringReceptionistExtension,
                                             shardingExt: BgClusteringShardingExtension,
                                             actorFinder: JournalReader,
                                             actorRefFactory: ActorRefFactory,
                                             classTag: ClassTag[I])
  : SatelliteContext[I, V] = {
    new SatelliteContext[I, V](name, handler)
  }

  trait ListenerStartupNotifier {
    def notifyStartupValues(implicit
                            executionContext: ExecutionContext,
                            timeout: Timeout,
                            sender: ActorRef = ActorRef.noSender)
    : Future[Map[PersistenceId, Throwable]]
  }

  trait VersionedCache[R] {
    def getValue(id: VersionedId)
                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[R]

    def getVersion(id: String)
                  (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[VersionedId]

    def addListener[C <: NewVersionedValueListener[R]](listener: C): ListenerStartupNotifier
  }

  class SatelliteContext[I, V] private[DistributedSharedState](name: String,
                                                               handler: SatelliteValueHandler[I, V])
                                                              (implicit
                                                               receptionistExt: BgClusteringReceptionistExtension,
                                                               shardingExt: BgClusteringShardingExtension,
                                                               actorRefFactory: ActorRefFactory,
                                                               actorFinder: JournalReader,
                                                               classTag: ClassTag[I]) {
    private val listeners = Promise[Seq[DelayedSubsActor.Listener[VersionedId, V]]]()
    private val pubSub = DelayedSubsActor.start(DelayedSubsActor.Settings(s"satellite-listener-$name", listeners.future))
    private val pubSubSatellite = DelayedSubsActor.asSatelliteStateListener(pubSub)
    val satelliteStateActor: SatelliteStateActor.Protocol[I, V] = SatelliteStateActor.startSharded[I, V](
      SatelliteStateActor.Settings(name, handler, pubSubSatellite), receptionist = Some(receptionistExt))
    private var listenersSoFar: List[DelayedSubsActor.Listener[VersionedId, V]] = List()
    private var enabled: Option[Boolean] = None

    def createCache[R](cacheName: String, valueEnricher: V => Future[R], cacheTtl: FiniteDuration)
    : VersionedCache[R] = {
      val keyFactory: VersionedEntityActor.Protocol.GetValue => VersionedId = (it) => it.messageId
      val valueOwnerFacade = new ValueOwnerFacade[VersionedEntityActor.Protocol.GetValue, VersionedId, V] {
        override def apply(getterMessage: VersionedEntityActor.Protocol.GetValue)
                          (implicit executionContext: ExecutionContext, sender: ActorRef)
        : Future[OwnerValueResult[VersionedId, V]] = {
          satelliteStateActor.getValue(getterMessage).map {
            case GetValueOk(_, x) => OwnerValueOk(keyFactory.apply(getterMessage), x)
            case GetValueNotOk(_, ex) => OwnerValueNotOk(keyFactory.apply(getterMessage), ex)
          }
        }
      }
      val cacheAccessor = CacheAccessActor.start[VersionedEntityActor.Protocol.GetValue, VersionedId, R, V](CacheAccessActor.Settings(
        s"satellite-cache-$name-$cacheName", keyFactory, valueEnricher, valueOwnerFacade, cacheTtl))
      new VersionedCacheImpl[R](cacheName, satelliteStateActor, cacheAccessor)
    }

    def addGlobalListener(listenerName: String, onNewVersion: NewVersionedValueListener[V]): ListenerStartupNotifier = {
      _addGlobalListener(listenerName, onNewVersion)
      new ListenerStartupNotifier {
        def notifyStartupValues(implicit executionContext: ExecutionContext,
                                timeout: Timeout, sender: ActorRef = ActorRef.noSender)
        : Future[Map[PersistenceId, Throwable]] = {
          actorFinder.allActorsAsync(s"satellite-state-$name")
            .flatMap { idsFound =>
              val listOfFutures = idsFound.map { id =>
                val statusUpdate = for (
                  valueResult <- satelliteStateActor.getLatestValue(GetLatestValue(id.uniqueId));
                  status <- valueResult match {
                    case GetLatestValueOk(_, ver, value) =>
                      onNewVersion.onNewVersionAsk(ver, value)
                    case GetLatestValueNotOk(_, ver, ex) =>
                      Future.successful(NewVersionNotOk(ver, ex))
                  }
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
    }

    private def _addGlobalListener(listenerName: String, onNewVersion: NewVersionedValueListener[V]): Unit = {
      enabled.foreach(_ => throw new Exception("cannot add listeners to enabled context"))
      val listenerObject: DelayedSubsActor.Listener[VersionedId, V] = new DelayedSubsActor.Listener[VersionedId, V] {
        override def publish(publishMessage: Publish[VersionedId, V])
                            (implicit executionContext: ExecutionContext, sender: ActorRef):
        Future[PublishResult[VersionedId]] = onNewVersion.onNewVersionAsk(publishMessage.messageId, publishMessage.payload)(executionContext, sender, publishMessage.ackTimeout).map {
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

    class VersionedCacheImpl[R] private[distsharedstate](cacheName: String,
                                                         versionedEntity: VersionedEntityActor.ProtocolGetters[_],
                                                         cacheWrapped: CacheAccessActor.Protocol[VersionedEntityActor.Protocol.GetValue, VersionedId, R]
                                                        )
      extends VersionedCache[R] {
      def addListener[C <: NewVersionedValueListener[R]](listener: C): ListenerStartupNotifier = {

        enabled.foreach(_ => throw new Exception("cannot add listeners to enabled context"))
        val VRMappingListener: NewVersionedValueListener[V] = NewVersionedValueListener.adaptListener(listener) {
          (version, _, executionContext, sender, timeout) =>
            getValue(version)(executionContext, sender, timeout)
        }
        _addGlobalListener(cacheName, VRMappingListener)

        new ListenerStartupNotifier {
          def notifyStartupValues(implicit executionContext: ExecutionContext,
                                  timeout: Timeout, sender: ActorRef = ActorRef.noSender)
          : Future[Map[PersistenceId, Throwable]] = {
            actorFinder.allActorsAsync(s"satellite-state-$name")
              .flatMap { idsFound =>
                val listOfFutures = idsFound.map { id =>
                  val statusUpdate = for (
                    version <- getVersion(id.uniqueId);
                    value <- getValue(version);
                    status <- listener.onNewVersionAsk(version, value)
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
      }

      def getValue(id: VersionedId)
                  (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
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


  }

}