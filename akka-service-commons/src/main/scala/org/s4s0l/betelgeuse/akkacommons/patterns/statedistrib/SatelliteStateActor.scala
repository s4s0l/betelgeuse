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

import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.pattern.pipe
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionistExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.message.Message
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.Protocol.{StateDistributed, StateDistributedNotOk, StateDistributedOk, StateDistributedResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener.{StateChanged, StateChangedNotOk, StateChangedOk, StateChangedResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.ProtocolGetters
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.serialization.SimpleSerializer
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget
import org.s4s0l.betelgeuse.akkacommons.utils.QA._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag

/**
  * Adapts [[VersionedEntityActor]] to behave like [[SatelliteProtocol]], so is a remote part
  * of distributed state pattern, also it shares implementation of [[VersionedEntityActor]]
  * it has incompatible protocol as it does not support optimistic lock semantics for
  * new versions of objects,
  *
  * @author Marcin Wielgus
  */
class SatelliteStateActor[T](settings: Settings[T])(implicit classTag: ClassTag[T])
  extends VersionedEntityActor[T](VersionedEntityActor.Settings(settings.name)) {

  private lazy val serializer = SimpleSerializer(context.system)

  implicit val messageForward: Message.ForwardHeaderProvider = Message.defaultForward

  override def receiveCommand: Receive = super.receiveCommand orElse {

    case msg@Message("state-change", _, _, _) =>
      implicit val executionContext: ExecutionContext = context.dispatcher
      val objectValue = msg.payload.asObject[T](classTag, serializer)
      val versionedId = VersionedId(msg.get("versionedId").get)
      val time: Timeout = msg.ttlAsDurationLeft
      Protocol(self, shardName).wrappedProtocol.setVersionedValue(SetVersionedValue(versionedId, objectValue))(executionContext, self, time)
        .map {
          case _: SetValueOk => msg.response("state-change-ok", "")
          case err: SetValueNotOk => msg.response("state-change-not-ok", err.ex.getMessage).withFailed()
        }
        .recover {
          case x: Throwable =>
            log.error(x, "Error adapting message to self")
            msg.response("state-change-not-ok", x.getMessage).withFailed()
        }.pipeTo(sender())

    case msg@Message("distribution-complete", _, _, _) =>
      implicit val executionContext: ExecutionContext = context.dispatcher
      val senderTmp = sender()
      val versionedId = VersionedId(msg.get("versionedId").get)
      val option = getValueAtVersion(versionedId)
      if (option.isDefined) {
        option.foreach { it =>
          import org.s4s0l.betelgeuse.utils.AllUtils._
          settings.listener.configurationChanged(StateChanged(versionedId, it, msg.ttlAsDurationLeft))
            .map {
              case StateChangedOk(_) => msg.response("distribution-complete-ok", "")
              case StateChangedNotOk(_, ex) => msg.response("distribution-complete-not-ok", ex.getMessage).withFailed()
            }
            .recover { case it: Throwable =>
              if (log.isDebugEnabled)
                log.error(it, "Unable to confirm StateDistributed for SatelliteState {}, id={}", settings.name, versionedId)
              msg.response("distribution-complete-not-ok", it.getMessage).withFailed()
            }
            .pipeToWithTimeout(senderTmp, msg.ttlAsDurationLeft,
              msg.response("distribution-complete-not-ok", "Timeout!").withFailed(), context.system.scheduler)
        }
      } else {
        senderTmp ! msg.response("distribution-complete-not-ok", s"No value at version $versionedId  ").withFailed()
      }


    case StateDistributed(uuid, versionedId, expDuration) =>
      val senderTmp = sender()
      val option = getValueAtVersion(versionedId)
      if (option.isDefined) {
        option.foreach { it =>
          import context.dispatcher
          import org.s4s0l.betelgeuse.utils.AllUtils._
          settings.listener.configurationChanged(StateChanged(versionedId, it, expDuration))
            .map {
              case StateChangedOk(_) => StateDistributedOk(uuid)
              case StateChangedNotOk(_, ex) => StateDistributedNotOk(uuid, ex)
            }
            .recover { case it: Throwable =>
              if (log.isDebugEnabled)
                log.error(it, "Unable to confirm StateDistributed for SatelliteState {}, id={}", settings.name, versionedId)
              StateDistributedNotOk(uuid, it)
            }
            .pipeToWithTimeout(senderTmp, expDuration,
              StateDistributedNotOk(uuid, new Exception("Timeout!!!")), context.system.scheduler)
        }
      } else {
        senderTmp ! StateDistributedNotOk(uuid, new Exception(s"No value at version $versionedId  "))
      }
  }

  /**
    * Unlike [[VersionedEntityActor]] we accept all versions that are unknown to us
    */
  override protected def isVersionAccepted(version: VersionedId): Boolean = getValueAtVersion(version).isEmpty

}

object SatelliteStateActor {

  def startSharded[T](settings: Settings[T],
                      propsMapper: Props => Props = identity,
                      receptionist: Option[BgClusteringReceptionistExtension] = None)
                     (implicit shardingExt: BgClusteringShardingExtension, classTag: ClassTag[T])
  : Protocol[T] = {
    val ref = shardingExt.start(s"satellite-state-${settings.name}", Props(new SatelliteStateActor[T](settings)), entityExtractor)
    receptionist.foreach(_.registerByName(getRemoteName(settings.name), ref))
    Protocol(ref, settings.name)
  }

  trait SatelliteStateListener[T] {
    def configurationChanged(msg: StateChanged[T])
                            (implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender)
    : Future[StateChangedResult]
  }

  def getRemoteName(name: String): String = s"/user/satellite-state-$name"

  private def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: IncomingMessage => (a.entityId, a)
    case a: StateDistributed => (a.versionedId.id, a)
    case a@Message("distribution-complete" | "state-change", _, _, _) if a.get("versionedId").isDefined => (VersionedId(a("versionedId")).id, a)
  }

  final case class Settings[T](name: String, listener: SatelliteStateListener[T])

  /**
    * An protocol for [[SatelliteStateActor]]
    */
  final class Protocol[T] private[statedistrib](protected[statedistrib] val actorTarget: ActorTarget, shardName: String)
    extends ProtocolGetters[T] with
      SatelliteProtocol[T] {

    private[statedistrib] val wrappedProtocol = VersionedEntityActor.Protocol[T](actorTarget, shardName)

    /**
      * distributes state change
      */
    def stateChange(msg: StateChange[T])
                   (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[StateChangeResult] = {
      wrappedProtocol.setVersionedValue(SetVersionedValue(msg.versionedId, msg.value))(executionContext, sender, msg.expectedConfirmIn)
        .map {
          case _: SetValueOk => StateChangeOk(msg.messageId)
          case err: SetValueNotOk => StateChangeNotOk(msg.messageId, err.ex)
        }
        .recover {
          case x: Throwable => StateChangeNotOk(msg.messageId, x)
        }
    }

    /**
      * informs that all destinations confirmed
      */
    def distributionComplete(msg: DistributionComplete)
                            (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[DistributionCompleteResult] = {
      actorTarget.?(StateDistributed(msg.messageId, msg.versionedId, msg.expectedConfirmIn))(msg.expectedConfirmIn, sender)
        .mapTo[StateDistributedResult]
        .map {
          case StateDistributedOk(_) => DistributionCompleteOk(msg.messageId)
          case StateDistributedNotOk(_, ex) => DistributionCompleteNotOk(msg.messageId, ex)
        }
        .recover { case ex: Throwable => DistributionCompleteNotOk(msg.messageId, ex) }
    }
  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorTarget: ActorTarget, shardName: String): Protocol[T] = new Protocol(actorTarget, shardName)

    private[statedistrib] sealed trait StateDistributedResult extends NullResult[Uuid]

    private[statedistrib] case class StateDistributed(messageId: Uuid, versionedId: VersionedId, expectedConfirmIn: FiniteDuration) extends Question[Uuid]

    private[statedistrib] case class StateDistributedOk(correlationId: Uuid) extends StateDistributedResult with OkNullResult[Uuid]

    private[statedistrib] case class StateDistributedNotOk(correlationId: Uuid, ex: Throwable) extends StateDistributedResult with NotOkNullResult[Uuid]

  }

  object SatelliteStateListener {

    case class StateChanged[T](messageId: VersionedId, value: T, expDuration: FiniteDuration) extends Question[VersionedId]

    sealed trait StateChangedResult extends NullResult[VersionedId]

    case class StateChangedOk(correlationId: VersionedId) extends StateChangedResult with OkNullResult[VersionedId]

    case class StateChangedNotOk(correlationId: VersionedId, ex: Throwable) extends StateChangedResult with NotOkNullResult[VersionedId]

  }


}