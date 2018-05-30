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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import java.sql.SQLException

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence.{BuiltInSerializerHints, PersistentRepr}
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, PersistenceId, ScalikeAsyncWriteJournal, ScalikeAsyncWriteJournalDao}
import org.s4s0l.betelgeuse.akkacommons.serialization._
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Maciej Flak
  */
class RoachAsyncWriteJournal
  extends ScalikeAsyncWriteJournal[RoachAsyncWriteJournalEntity]() {

  override val dao: ScalikeAsyncWriteJournalDao[RoachAsyncWriteJournalEntity] = new RoachAsyncWriteJournalDao()
  private implicit val jsonSerializer: JacksonJsonSerializer = RoachAsyncWriteJournal.jsonSerializer(context.system)
  private implicit val simpleSerializer: SimpleSerializer = RoachAsyncWriteJournal.simpleSerializer(context.system)
  private implicit val hints: RoachSerializerHints = RoachAsyncWriteJournal
    .getSerializerHints(context.system.settings.config.getConfig(getId))

  def getId: String = "persistence-journal-roach"

  override def mapExceptions(session: DBSession)
  : PartialFunction[Exception, Exception] = RoachAsyncWriteJournal.mapExceptions(session)


  override def createEntity(representation: PersistentRepr): RoachAsyncWriteJournalEntity = {
    RoachAsyncWriteJournal.createEntity(representation)
  }

  override def createRepresentation(entity: RoachAsyncWriteJournalEntity)
  : PersistentRepr = {
    RoachAsyncWriteJournal.createRepresentation(entity)
  }

  /**
    * asynchronously deletes all persistent messages up to `toSequenceNr`
    *
    * @param persistenceId - id
    * @param toSequenceNr  - num of max sequence that should be deleted
    * @return
    */
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = Future {
    dbAccess.update { implicit session =>
      val id: PersistenceId = PersistenceId.fromString(persistenceId)
      dao.deleteUpTo(id.tag, id.uniqueId, toSequenceNr)
    }
  }
}

private[roach] object RoachAsyncWriteJournal {

  def mapExceptions(session: DBSession): PartialFunction[Exception, Exception] = {
    case sql: SQLException if sql.getMessage.contains("duplicate key value") =>
      new JurnalDuplicateKeyException("Key duplicated", sql)
  }

  def jsonSerializer(system: ActorSystem): JacksonJsonSerializer = {
    new JacksonJsonSerializer()
  }

  def simpleSerializer(system: ActorSystem): SimpleSerializer = {
    SimpleSerializer(system)
  }

  def createEntity(representation: PersistentRepr)
                  (implicit jsonSerializer: JacksonJsonSerializer,
                   simpleSerializer: SimpleSerializer,
                   serializerHints: RoachSerializerHints)
  : RoachAsyncWriteJournalEntity = {
    val persistenceId = PersistenceId.fromString(representation.persistenceId)
    val serialized = serialize(representation.payload)
    new RoachAsyncWriteJournalEntity(
      persistenceId.tag,
      persistenceId.uniqueId,
      representation.sequenceNr,
      representation.manifest,
      representation.writerUuid,
      serialized.value,
      serialized.valueClass,
      representation.deleted
    )
  }

  def createRepresentation(entity: RoachAsyncWriteJournalEntity)
                          (implicit jsonSerializer: JacksonJsonSerializer,
                           simpleSerializer: SimpleSerializer,
                           serializerHints: RoachSerializerHints)
  : PersistentRepr = {

    val event = deserialize(entity.event, entity.eventClass)
    val persistenceId = PersistenceId(entity.tag, entity.id)
    PersistentRepr.apply(
      event,
      entity.seq,
      persistenceId.toString,
      entity.manifest,
      entity.deleted,
      ActorRef.noSender,
      entity.writerUuid
    )
  }


  def serialize(value: Any)
               (implicit jsonSerializer: JacksonJsonSerializer,
                simpleSerializer: SimpleSerializer,
                serializerHints: RoachSerializerHints)
  : Serialized = {
    value match {
      case jsonCapableValue: JacksonJsonSerializable =>
        Serialized(
          jsonSerializer.asSimple.toString(jsonCapableValue),
          value.getClass.getName)

      case wrappingCandidate
        if serializerHints.wrap.isDefinedAt(wrappingCandidate) =>
        val jsonSerializable = serializerHints.wrap.apply(wrappingCandidate)
        Serialized(
          jsonSerializer.asSimple.toString(jsonSerializable),
          jsonSerializable.getClass.getName)

      case jsonCapableCandidate: AnyRef
        if serializerHints.useJackson.isDefinedAt(jsonCapableCandidate)
          && serializerHints.useJackson(jsonCapableCandidate) =>
        Serialized(
          jsonSerializer.asSimple.toString(jsonCapableCandidate),
          value.getClass.getName)

      case binary: AnyRef
        if serializerHints.useBinary.isDefinedAt(binary)
          && serializerHints.useBinary(binary) =>
        Serialized(
          jsonSerializer.asSimple.toString(JsonBinaryWrapper(binary.getClass.getName,
            simpleSerializer.toBinary(binary))),
          classOf[JsonBinaryWrapper].getName)

      case _ => throw new ClassCastException(s"Event of class ${value.getClass.getName} does not implement JacksonJsonSerializable!!!")
    }

  }


  def deserialize(serializedValue: String, valueClass: String)
                 (implicit jsonSerializer: JacksonJsonSerializer,
                  simpleSerializer: SimpleSerializer,
                  serializerHints: RoachSerializerHints): Any = {
    val eventClass = Class.forName(valueClass).asInstanceOf[Class[AnyRef]]
    jsonSerializer.asSimple.fromStringToClass(serializedValue, eventClass) match {

      case JsonBinaryWrapper(className, binary) =>
        simpleSerializer.fromBinaryToClass[AnyRef](binary, Class.forName(className).asInstanceOf[Class[AnyRef]])

      case wrapped: RoachSerializerHints.HintWrapped =>
        if (serializerHints.unwrap.isDefinedAt(wrapped)) {
          serializerHints.unwrap.apply(wrapped)
        } else {
          throw new Exception(s"Class ${wrapped.getClass.getName} is HintWrapped but hit cannot deserialize it!")
        }


      case x => x
    }
  }

  def getSerializerHints(config: Config): RoachSerializerHints = {
    import org.s4s0l.betelgeuse.utils.AllUtils._
    config.string("serializerHintsClass")
      .map(Class.forName(_).asInstanceOf[Class[RoachSerializerHints]])
      .map(_.newInstance())
      .map(_.orElse(new BuiltInSerializerHints()))
      .getOrElse(new BuiltInSerializerHints(): RoachSerializerHints)
  }

  case class Serialized(value: String, valueClass: String)

}