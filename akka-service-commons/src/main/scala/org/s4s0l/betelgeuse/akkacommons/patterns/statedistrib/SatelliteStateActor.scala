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

import akka.actor.Status.{Failure, Status, Success}
import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.pattern.pipe
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BetelgeuseAkkaClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.{SatelliteStateListenerResponse, Settings, StateDistributed, StateDistributedConfirm}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.{IncomingMessage, SetVersionedValue, ValueUpdateOptimisticError, ValueUpdated}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class SatelliteStateActor[T](settings: Settings[T]) extends VersionedEntityActor(VersionedEntityActor.Settings(settings.name)) {

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case msg@StateDistributed(versionedId, _) =>
      val senderTmp = sender()
      val option = getValueAtVersion(versionedId)
      if (option.isDefined) {
        option.foreach { it =>
          import context.dispatcher
          settings.listener.configurationChanged(versionedId, it.asInstanceOf[T])
            .map(it => SatelliteStateListenerResponse(it, msg, senderTmp))
            .recover { case it: Throwable => SatelliteStateListenerResponse(Failure(it), msg, senderTmp) }
            .pipeTo(self)
        }
      } else {
        senderTmp ! StateDistributedConfirm(versionedId, msg.destination, ok = false)
      }


    case SatelliteStateListenerResponse(Success(_), StateDistributed(versionedId, dest), originalSender) =>
      originalSender ! StateDistributedConfirm(versionedId, dest, ok = true);

    case SatelliteStateListenerResponse(Failure(ex), StateDistributed(versionedId, dest), originalSender) =>
      if (log.isDebugEnabled)
        log.error(ex, "Unable to confirm StateDistributed for SatelliteState {}, id={}", settings.name, versionedId)
      originalSender ! StateDistributedConfirm(versionedId, dest, ok = false);
  }

}

object SatelliteStateActor {

  def startSharded[T](settings: Settings[T], propsMapper: Props => Props = identity)
                     (implicit shardingExt: BetelgeuseAkkaClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, Props(new SatelliteStateActor[T](settings)), entityExtractor)
    Protocol(ref)
  }

  private def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: IncomingMessage => (a.id, a)
    case a: StateDistributed => (a.versionedId.id, a)
  }

  trait SatelliteStateListener[T] {
    def configurationChanged(versionedId: VersionedId, value: T)
                            (implicit executionContext: ExecutionContext): Future[Status]
  }

  final case class Settings[T](name: String, listener: SatelliteStateListener[T])

  /**
    * An protocol for [[SatelliteStateActor]]
    */
  final class Protocol[T] private(actorRef: => ActorRef) extends VersionedEntityActor.Protocol[T](actorRef)
    with SatelliteProtocol[T] {


    /**
      * distributes state change
      */
    override def stateChanged(versionedId: VersionedId, value: T, destination: String)
                             (implicit timeout: Timeout, executionContext: ExecutionContext): Future[Status] = {
      setVersionedValue(SetVersionedValue(versionedId, value, Some(destination)))
        .map {
          case ok: ValueUpdated => Success(ok)
          case err: ValueUpdateOptimisticError => Failure(new Exception(err.toString))
        }
        .recover {
          case x: Throwable => Failure(x)
        }
    }

    /**
      * informs that all destinations confirmed
      */
    override def stateDistributed(versionedId: VersionedId, destination: String)
                                 (implicit timeout: Timeout, executionContext: ExecutionContext): Future[Status] = {
      import akka.pattern.ask
      actorRef.ask(StateDistributed(versionedId, destination))
        .mapTo[StateDistributedConfirm]
        .map {
          case msg@StateDistributedConfirm(_, _, true) => Success(msg)
          case StateDistributedConfirm(_, _, false) => Failure(new Exception(s"Unable to distribute confirmation to destination $destination for version $versionedId"))
        }
        .recover { case ex: Throwable => Failure(ex) }
    }


  }

  private case class SatelliteStateListenerResponse(status: Status, originalMessage: StateDistributed, originalSender: ActorRef)

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: => ActorRef): Protocol[T] = new Protocol(actorRef)


  }

  private case class StateDistributed(versionedId: VersionedId, destination: String)

  private case class StateDistributedConfirm(versionedId: VersionedId, destination: String, ok: Boolean)

}    