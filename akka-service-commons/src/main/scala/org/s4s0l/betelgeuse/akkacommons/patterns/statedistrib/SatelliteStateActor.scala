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

import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionistExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.Protocol.{NotOk, Ok, StateDistributed, StateDistributedConfirm}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener.{StateChanged, StateChangedNotOk, StateChangedOk, StateChangedResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget
import org.s4s0l.betelgeuse.akkacommons.utils.QA._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class SatelliteStateActor[T](settings: Settings[T])
  extends VersionedEntityActor(VersionedEntityActor.Settings(settings.name)) {

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case StateDistributed(uuid, versionedId, expDuration) =>
      val senderTmp = sender()
      val option = getValueAtVersion(versionedId)
      if (option.isDefined) {
        option.foreach { it =>
          import context.dispatcher
          import org.s4s0l.betelgeuse.utils.AllUtils._
          settings.listener.configurationChanged(StateChanged(versionedId, it.asInstanceOf[T], expDuration))
            .map {
              case StateChangedOk(_) => Ok(uuid)
              case StateChangedNotOk(_, ex) => NotOk(uuid, ex)
            }
            .recover { case it: Throwable =>
              if (log.isDebugEnabled)
                log.error(it, "Unable to confirm StateDistributed for SatelliteState {}, id={}", settings.name, versionedId)
              NotOk(uuid, it)
            }
            .pipeToWithTimeout(senderTmp, expDuration,
              NotOk(uuid, new Exception("Timeout!!!")), context.system.scheduler)
        }
      } else {
        senderTmp ! NotOk(uuid, new Exception(s"No value at version $versionedId  "))
      }
  }

}

object SatelliteStateActor {

  def startSharded[T](settings: Settings[T],
                      propsMapper: Props => Props = identity,
                      receptionist: Option[BgClusteringReceptionistExtension] = None)
                     (implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(s"/user/satellite-state-${settings.name}", Props(new SatelliteStateActor[T](settings)), entityExtractor)
    receptionist.foreach(_.registerByName(getRemoteName(settings.name), ref))
    Protocol(ref)
  }

  def getRemoteName(name: String): String = s"/user/satellite-state-$name"

  private def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: IncomingMessage => (a.entityId, a)
    case a: StateDistributed => (a.versionedId.id, a)
  }

  trait SatelliteStateListener[T] {
    def configurationChanged(msg: StateChanged[T])
                            (implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender)
    : Future[StateChangedResult]
  }

  /**
    * An protocol for [[SatelliteStateActor]]
    */
  final class Protocol[T] private(actorTarget: ActorTarget)
    extends VersionedEntityActor.Protocol[T](actorTarget)
      with SatelliteProtocol[T] {


    /**
      * distributes state change
      */
    def stateChanged(msg: StateChange[T])
                    (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[StateChangeResult] = {
      setVersionedValue(SetVersionedValue(msg.versionedId, msg.value))(executionContext, sender, msg.expectedConfirmIn)
        .map {
          case _: SetValueOk => ChangeOk(msg.messageId)
          case err: SetValueNotOk => ChangeNotOk(msg.messageId, err.ex)
        }
        .recover {
          case x: Throwable => ChangeNotOk(msg.messageId, x)
        }
    }

    /**
      * informs that all destinations confirmed
      */
    def stateDistributed(msg: DistributionComplete)
                        (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[DistributionResult] = {
      actorTarget.?(StateDistributed(msg.messageId, msg.versionedId, msg.expectedConfirmIn))(msg.expectedConfirmIn, sender)
        .mapTo[StateDistributedConfirm]
        .map {
          case Ok(_) => DistributionOk(msg.messageId)
          case NotOk(_, ex) => DistributionNotOk(msg.messageId, ex)
        }
        .recover { case ex: Throwable => DistributionNotOk(msg.messageId, ex) }
    }
  }

  final case class Settings[T](name: String, listener: SatelliteStateListener[T])

  object SatelliteStateListener {

    sealed trait StateChangedResult extends NullResult[VersionedId]

    case class StateChanged[T](messageId: VersionedId, value: T, expDuration: FiniteDuration) extends Question[VersionedId]

    case class StateChangedOk(correlationId: VersionedId) extends StateChangedResult with OkNullResult[VersionedId]

    case class StateChangedNotOk(correlationId: VersionedId, ex: Throwable) extends StateChangedResult with NotOkNullResult[VersionedId]

  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorTarget: ActorTarget): Protocol[T] = new Protocol(actorTarget)

    sealed trait StateDistributedConfirm extends NullResult[Uuid]

    case class StateDistributed(messageId: Uuid, versionedId: VersionedId, expectedConfirmIn: FiniteDuration) extends Question[Uuid]

    case class Ok(correlationId: Uuid) extends StateDistributedConfirm with OkNullResult[Uuid]

    case class NotOk(correlationId: Uuid, ex: Throwable) extends StateDistributedConfirm with NotOkNullResult[Uuid]

  }


}