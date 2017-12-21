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
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Settings
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.PersistentShardedActor
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, QA, TimeoutShardedActor}

import scala.concurrent.{ExecutionContext, Future}
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
class VersionedEntityActor[T](settings: Settings) extends Actor
  with ActorLogging
  with PersistentShardedActor
  with TimeoutShardedActor {

  private var currentVersion: Int = 0
  private var currentValue: Option[T] = None
  private var versionMap = Map[Int, T]()

  import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Events._

  override def receiveRecover: Receive = {
    case e: CqrsEvent if processEvent(true).isDefinedAt(e) => processEvent(true)
    case _: RecoveryCompleted => recoveryCompleted()
  }

  protected def recoveryCompleted(): Unit = {}

  override def receiveCommand: Receive = {
    case m: IncomingMessage => m match {
      case req@GetValueVersion(_, _) =>
        sender() ! ValueVersionResult(req.messageId, getCurrentVersionId)
      case GetValue(v) =>
        getValueAtVersion(v) match {
          case Some(value) =>
            sender() ! ValueOk(v, value)
          case None =>
            sender() ! ValueNotOk(v, ValueMissingException(v))
        }
      case GetLatestValue(id, messageId) =>
        val v = VersionedId(id, currentVersion)
        getValueAtVersion(v) match {
          case Some(value) =>
            sender() ! ValueOk(messageId, (v, value))
          case None =>
            sender() ! ValueNotOk(messageId, ValueMissingException(v))
        }
      case cmd@SetValue(id, newValue, _) =>
        val v = VersionedId(id, currentVersion + 1)
        persist(ValueEvent(cmd.messageId, v, interceptNewVersion(v, newValue.asInstanceOf[T])))(processEvent(false))
      case cmd@SetVersionedValue(v, newValue, _) =>
        if (isVersionAccepted(v)) {
          persist(ValueEvent(cmd.messageId, v, interceptNewVersion(v, newValue.asInstanceOf[T])))(processEvent(false))
        } else if (getValueAtVersion(v).contains(newValue)) {
          confirmUpdated(cmd.messageId, v)
        } else {
          confirmOptimisticError(cmd.asInstanceOf[SetVersionedValue[T]])
        }
    }
  }

  /**
    * called before new version is persisted. returned value will be used as new value
    * passed down to event. By default returns value
    */
  protected def interceptNewVersion(versionedId: VersionedId, value: T): T = value

  /**
    * tests whether new version should be accepted as new value, by
    *
    * default only version of number +1 current version is accepted
    *
    */
  protected def isVersionAccepted(version: VersionedId): Boolean = version.version == currentVersion + 1

  def processEvent(recover: Boolean): PartialFunction[Any, Unit] = {
    case ve@ValueEvent(_, versionedId, value) =>
      if (versionedId.version > currentVersion) {
        currentVersion = versionedId.version
        currentValue = Some(value.asInstanceOf[T])
      }
      versionMap = versionMap + (versionedId.version -> value.asInstanceOf[T])
      valueUpdated(versionedId, value.asInstanceOf[T])
      if (!recover)
        confirmUpdated(ve.uuid, versionedId)
  }

  /**
    * called on successful event persistence
    */
  protected def valueUpdated(versionedId: VersionedId, value: T): Unit = {}

  /**
    * confirms successful processing of command, not event!
    *
    */
  protected def confirmUpdated(uuid: Uuid, versionedId: VersionedId): Unit = {
    sender() ! SetValueOk(uuid, versionedId)
  }

  protected def confirmOptimisticError(cmd: SetVersionedValue[T]): Unit = {
    sender() ! SetValueNotOk(cmd.messageId, ValueUpdateOptimisticException(getCurrentVersionId))
  }

  protected def getCurrentVersionId: VersionedId = {
    VersionedId(shardedActorId, currentVersion)
  }

  protected def getValueAtVersion(versionedId: VersionedId): Option[T] = versionMap.get(versionedId.version)
}


object VersionedEntityActor {

  def startSharded[T](settings: Settings, propsMapper: Props => Props = identity)
                     (implicit shardingExt: BgClusteringShardingExtension)
  : Protocol[T] = {
    val ref = shardingExt.start(settings.name, props[T](settings), entityExtractor)
    Protocol(ref, settings.name)
  }

  private def props[T](settings: Settings): Props = {
    Props(new VersionedEntityActor[T](settings))
  }

  def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: IncomingMessage => (a.entityId, a)
  }

  final case class Settings(name: String)


  trait ProtocolGetters[T] {

    import concurrent.duration._

    protected def actorTarget: ActorTarget

    /**
      * Gets current version for entity of given id
      *
      * @return current version (last)
      */
    def getVersion(msg: GetValueVersion)
                  (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[ValueVersionResult] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[ValueVersionResult]

    /**
      * Gets a value of a given id and version.
      *
      * @return a value with version
      */
    def getValue(msg: GetValue)
                (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[GetValueResult[T, VersionedId]] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[GetValueResult[T, VersionedId]]

    /**
      * Gets a latest value of a given id and version.
      *
      * @return a value with version
      */
    def getLatestValue(msg: GetLatestValue)
                      (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[GetValueResult[(VersionedId, T), Uuid]] =
      actorTarget.?(msg)(5 seconds, sender).mapTo[GetValueResult[(VersionedId, T), Uuid]]
  }

  /**
    * An protocol for [[VersionedEntityActor]]
    */
  class Protocol[T] protected(protected val actorTarget: ActorTarget, _shardName: String) extends ProtocolGetters[T] {

    import concurrent.duration._

    def shardName: String = _shardName

    /**
      * Sets a value of entity of given id.
      *
      * @return id and version of updated entity
      */
    def setValue(msg: SetValue[T])
                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout = 5 seconds)
    : Future[ValueUpdateResult] =
      actorTarget.?(msg)(timeout, sender).mapTo[ValueUpdateResult]

    /**
      * same as [[setValue()]] but without ask pattern, response of type [[SetValueOk]] will be delivered to
      * sender
      */
    def setValueMsg(msg: SetValue[T])
                   (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Unit =
      actorTarget.!(msg)(sender)

    /**
      * Tries to do optimistically locked update.
      *
      * @param msg contains value and a desired version which MUST be currentEntityVersion + 1  or
      *            contain a version already present in entity and in that case value must be equal to that
      *            already present
      * @return if current version is -1 version given in msg then [[SetValueOk]] otherwise
      *         [[SetValueNotOk]] with exception [[ValueUpdateOptimisticException]] which will contain current version of entity
      */
    def setVersionedValue(msg: SetVersionedValue[T])
                         (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[ValueUpdateResult] =
      actorTarget.?(msg).mapTo[ValueUpdateResult]

    /**
      * same as [[setVersionedValue()]] but without ask pattern, response of type [[ValueUpdateResult]] will be delivered to
      * sender
      */
    def setVersionedValueMsg(msg: SetVersionedValue[T])
                            (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Unit =
      actorTarget.!(msg)(sender)
  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorTarget: ActorTarget, shardName: String): Protocol[T] = new Protocol[T](actorTarget, shardName)

    sealed trait IncomingMessage {
      def entityId: String
    }

    sealed trait ValueUpdateResult extends Result[Uuid, VersionedId]

    trait GetValueResult[T, V] extends Result[V, T]

    case class GetValueVersion(entityId: String, messageId: Uuid = QA.uuid) extends IncomingMessage with UuidQuestion

    case class ValueVersionResult(correlationId: Uuid, value: VersionedId) extends OkResult[Uuid, VersionedId]

    case class GetValue(messageId: VersionedId) extends IncomingMessage with Question[VersionedId] {
      override def entityId: String = messageId.id
    }

    case class GetLatestValue(entityId: String, messageId: QA.Uuid = QA.uuid) extends IncomingMessage with Question[QA.Uuid]

    case class ValueOk[T, V](correlationId: V, value: T) extends GetValueResult[T, V] with OkResult[V, T]

    case class ValueNotOk[T, V](correlationId: V, ex: Throwable) extends GetValueResult[T, V] with NotOkResult[V, T]

    case class SetValue[T](entityId: String, value: T, messageId: Uuid = QA.uuid) extends IncomingMessage with UuidQuestion

    case class SetVersionedValue[T](versionedId: VersionedId, value: T, messageId: Uuid = QA.uuid) extends IncomingMessage with UuidQuestion {
      override def entityId: String = versionedId.id
    }

    case class SetValueOk(correlationId: Uuid, value: VersionedId) extends ValueUpdateResult with OkResult[Uuid, VersionedId]

    case class SetValueNotOk(correlationId: Uuid, ex: Throwable) extends ValueUpdateResult with NotOkResult[Uuid, VersionedId]


    case class ValueMissingException(versionedId: VersionedId) extends Exception

    /**
      * Contains current entity version, returned back as answer to [[SetVersionedValue]] when
      * optimistic lock has failed..
      *
      * @param versionedId version of entity at the time of update
      */
    case class ValueUpdateOptimisticException(versionedId: VersionedId) extends Exception()

  }

  private object Events {

    sealed trait CqrsEvent

    case class ValueEvent[T](uuid: Uuid, versionedId: VersionedId, value: T) extends CqrsEvent

  }

}
