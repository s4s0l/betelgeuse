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

package org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.RecoveryCompleted
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BetelgeuseAkkaClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Settings
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.PersistentShardedActor
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, TimeoutShardedActor}

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Persistent actor that holds some value which is versioned.
  * Can be queried for any version - current or past.
  *
  * TODO: this actor could be templated and strict check type of value
  *
  * @see [[org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol]]
  * @author Marcin Wielgus
  */
class VersionedEntityActor(settings: Settings) extends Actor
  with ActorLogging
  with PersistentShardedActor
  with TimeoutShardedActor {

  private var currentVersion: Int = 0
  private var currentValue: Option[Any] = None
  private var versionMap = Map[Int, Any]()

  import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Events._

  override def receiveRecover: Receive = {
    case e: CqrsEvent if processEvent(true).isDefinedAt(e) => processEvent(true)
    case _: RecoveryCompleted =>
    case e => throw new Exception(s"unable to recover, unknown or unsupported event $e")
  }

  override def receiveCommand: Receive = {
    case m: IncomingMessage => m match {
      case GetValueVersion(_) =>
        sender() ! ValueVersion(getCurrentVersionId)
      case GetValue(v) =>
        sender() ! Value(v, getValueAtVersion(v))
      case SetValue(id, newValue, tag) =>
        persist(ValueEvent(SetVersionedValue(VersionedId(id, currentVersion + 1), newValue, tag)))(processEvent(false))
      case cmd@SetVersionedValue(v@VersionedId(_, version), newValue, _) =>
        if (version == currentVersion + 1) {
          persist(ValueEvent(cmd))(processEvent(false))
        } else if (getValueAtVersion(v).contains(newValue)) {
          confirmUpdated(cmd)
        } else {
          confirmOptimisticError(cmd)
        }
    }
  }

  private def processEvent(recover: Boolean): PartialFunction[CqrsEvent, Unit] = {
    case ValueEvent(cmd) =>
      if (cmd.versionedId.version > currentVersion) {
        currentVersion = cmd.versionedId.version
        currentValue = Some(cmd.value)
      }
      versionMap = versionMap + (cmd.versionedId.version -> cmd.value)
      if (!recover)
        confirmUpdated(cmd)
  }

  protected def confirmUpdated(cmd: SetVersionedValue[Any]): Unit = {
    sender() ! ValueUpdated(cmd.versionedId, cmd.tag)
  }

  protected def confirmOptimisticError(cmd: SetVersionedValue[Any]): Unit = {
    sender() ! ValueUpdateOptimisticError(getCurrentVersionId, cmd.tag)
  }

  protected def getCurrentVersionId: VersionedId = {
    VersionedId(shardedActorId, currentVersion)
  }

  protected def getValueAtVersion(versionedId: VersionedId): Option[Any] = versionMap.get(versionedId.version)
}


object VersionedEntityActor {

  def startSharded[T](settings: Settings, propsMapper: Props => Props = identity)(implicit shardingExt: BetelgeuseAkkaClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, props(settings), entityExtractor)
    Protocol(ref)
  }

  private def props(settings: Settings): Props = {
    Props(new VersionedEntityActor(settings))
  }

  private def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: IncomingMessage => (a.id, a)
  }

  final case class Settings(name: String)

  /**
    * An protocol for [[VersionedEntityActor]]
    */
  class Protocol[T] protected(actorTarget: ActorTarget) {

    import concurrent.duration._

    /**
      * Gets current version for entity of given id
      *
      * @return current version (last)
      */
    def getVersion(msg: GetValueVersion)(implicit sender: ActorRef = Actor.noSender): Future[ValueVersion] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[ValueVersion]

    /**
      * Gets a value of a given id and version.
      *
      * @return a value with version
      */
    def getValue(msg: GetValue)(implicit sender: ActorRef = Actor.noSender): Future[Value[T]] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[Value[T]]

    /**
      * Sets a value of entity of given id.
      *
      * @return id and version of updated entity
      */
    def setValue(msg: SetValue[T])(implicit sender: ActorRef = Actor.noSender): Future[ValueUpdated] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[ValueUpdated]

    /**
      * same as [[setValue()]] but without ask pattern, response of type [[ValueUpdated]] will be delivered to
      * sender
      */
    def setValueMsg(msg: SetValue[T])(implicit sender: ActorRef = Actor.noSender): Unit =
      actorTarget.!(msg)(sender)

    /**
      * Tries to do optimistically locked update.
      *
      * @param msg contains value and a desired version which MUST be currentEntityVersion + 1  or
      *            contain a version already present in entity and in that case value must be equal to that
      *            already present
      * @return if current version is -1 version given in msg then [[ValueUpdated]] otherwise
      *         [[ValueUpdateOptimisticError]] which will contain current version of entity
      */
    def setVersionedValue(msg: SetVersionedValue[T])(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[ValueUpdateResult] =
      actorTarget.?(msg).mapTo[ValueUpdateResult]

    /**
      * same as [[setVersionedValue()]] but without ask pattern, response of type [[ValueUpdateResult]] will be delivered to
      * sender
      */
    def setVersionedValueMsg(msg: SetVersionedValue[T])(implicit sender: ActorRef = Actor.noSender): Unit =
      actorTarget.!(msg)(sender)
  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorTarget: ActorTarget): Protocol[T] = new Protocol[T](actorTarget)

    sealed trait IncomingMessage {
      def id: String
    }

    sealed trait OutgoingMessage


    case class GetValueVersion(id: String) extends IncomingMessage

    case class GetValue(versionedId: VersionedId) extends IncomingMessage {
      override def id: String = versionedId.id
    }

    //outgoing

    sealed trait ValueUpdateResult extends OutgoingMessage

    case class SetValue[T](id: String, value: T, tag: Option[Any] = None) extends IncomingMessage

    case class ValueVersion(versionedId: VersionedId) extends OutgoingMessage

    case class Value[T](versionedId: VersionedId, value: Option[T]) extends OutgoingMessage

    case class SetVersionedValue[T](versionedId: VersionedId, value: T, tag: Option[Any] = None) extends IncomingMessage {
      override def id: String = versionedId.id
    }

    case class ValueUpdated(versionedId: VersionedId, tag: Option[Any] = None) extends ValueUpdateResult

    /**
      * Contains current entity version, returned back as answer to [[SetVersionedValue]] when
      * optimistic lock has failed..
      *
      * @param versionedId version of entity at the time of update
      */
    case class ValueUpdateOptimisticError(versionedId: VersionedId, tag: Option[Any] = None) extends ValueUpdateResult

  }

  private object Events {

    sealed trait CqrsEvent

    case class ValueEvent[T](originCommand: SetVersionedValue[T]) extends CqrsEvent

  }


}
