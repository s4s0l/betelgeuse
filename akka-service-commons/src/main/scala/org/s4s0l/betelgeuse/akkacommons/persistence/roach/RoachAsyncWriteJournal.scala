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

import akka.actor.ActorRef
import akka.persistence.PersistentRepr
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, PersistenceId, ScalikeAsyncWriteJournal, ScalikeAsyncWriteJournalDao}
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Maciej Flak
  */
class RoachAsyncWriteJournal
  extends ScalikeAsyncWriteJournal[RoachAsyncWriteJournalEntity]() {

  override val dao: ScalikeAsyncWriteJournalDao[RoachAsyncWriteJournalEntity] = new RoachAsyncWriteJournalDao()
  private val config: Config = context.system.settings.config.getConfig(getId)
  private implicit val serializer: RoachSerializer = new RoachSerializer(context.system, config)

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


  def createEntity(representation: PersistentRepr)
                  (implicit serializer: RoachSerializer)
  : RoachAsyncWriteJournalEntity = {
    val persistenceId = PersistenceId.fromString(representation.persistenceId)
    val serialized = serializer.serialize(representation.payload)
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
                          (implicit serializer: RoachSerializer)
  : PersistentRepr = {

    val event = serializer.deserialize(entity.event, entity.eventClass)
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