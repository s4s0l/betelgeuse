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
import akka.persistence.PersistentRepr
import akka.serialization.SerializationExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, PersistenceId, ScalikeAsyncWriteJournal, ScalikeAsyncWriteJournalDao}
import org.s4s0l.betelgeuse.akkacommons.serialization.JsonAnyWrapper.StringWrapper
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer, JsonAnyWrapper, SimpleSerializer}
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Maciej Flak
  */
class RoachAsyncWriteJournal
  extends ScalikeAsyncWriteJournal[RoachAsyncWriteJournalEntity]() {

  override val dao: ScalikeAsyncWriteJournalDao[RoachAsyncWriteJournalEntity] = new RoachAsyncWriteJournalDao()
  private val jsonSerializer = RoachAsyncWriteJournal.jsonSerializer(context.system)

  override def mapExceptions(session: DBSession)
  : PartialFunction[Exception, Exception] = RoachAsyncWriteJournal.mapExceptions(session)


  override def createEntity(representation: PersistentRepr): RoachAsyncWriteJournalEntity = {
    RoachAsyncWriteJournal.createEntity(representation, jsonSerializer)
  }

  override def createRepresentation(entity: RoachAsyncWriteJournalEntity)
  : PersistentRepr = {
    RoachAsyncWriteJournal.createRepresentation(entity, jsonSerializer)
  }

  /**
    * asynchronously deletes all persistent messages up to `toSequenceNr`
    *
    * @param persistenceId - id
    * @param toSequenceNr  - num of max sequence that should be deleted
    * @return
    */
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = Future {
    dbAccess.query { implicit session =>
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
    JacksonJsonSerializer.get(SerializationExtension.get(system)).get
  }

  def createEntity(representation: PersistentRepr, serializer: JacksonJsonSerializer)
  : RoachAsyncWriteJournalEntity = {
    val jsonSerializer: SimpleSerializer = serializer.asSimple
    val persistenceId = PersistenceId.fromString(representation.persistenceId)
    val (eventClassName, serializedEvent) = representation.payload match {
      case jsonCapableValue: JacksonJsonSerializable =>
        (representation.payload.getClass.getName,
          jsonSerializer.toString(jsonCapableValue))
      case stringValue: String =>
        (
          classOf[JsonAnyWrapper].getName,
          jsonSerializer.toString(JsonAnyWrapper(Some(stringValue)))
        )
      case _ => throw new ClassCastException(s"Event of class ${representation.payload.getClass.getName} does not implement JacksonJsonSerializable!!!")
    }
    new RoachAsyncWriteJournalEntity(
      persistenceId.tag,
      persistenceId.uniqueId,
      representation.sequenceNr,
      representation.manifest,
      representation.writerUuid,
      serializedEvent,
      eventClassName,
      representation.deleted
    )
  }

  def createRepresentation(entity: RoachAsyncWriteJournalEntity,
                           serializer: JacksonJsonSerializer)
  : PersistentRepr = {

    val jsonSerializer: SimpleSerializer = serializer.asSimple
    val eventClass = Class.forName(entity.eventClass).asInstanceOf[Class[AnyRef]]
    val event = jsonSerializer.fromStringToClass(entity.event, eventClass) match {
      case JsonAnyWrapper(StringWrapper(value)) => value
      case x: JacksonJsonSerializable => x
    }
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
}