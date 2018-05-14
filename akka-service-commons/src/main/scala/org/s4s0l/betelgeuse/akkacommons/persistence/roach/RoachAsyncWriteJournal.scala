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

import akka.persistence.Persistence
import akka.serialization.{Serialization, SerializationExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, PersistenceId, ScalikeAsyncWriteJournal}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Maciej Flak
  */
class RoachAsyncWriteJournal
  extends ScalikeAsyncWriteJournal[RoachAsyncWriteJournalEntity]() {
  val extension = Persistence(context.system)
  private val serialization: Serialization = SerializationExtension.get(context.system)
  override val dao: RoachAsyncWriteJournalDao = new RoachAsyncWriteJournalDao(
    stringRef => extension.system.provider.resolveActorRef(stringRef),
    JacksonJsonSerializer.get(serialization).get
  )


  override def mapExceptions(session: DBSession): PartialFunction[Exception, Exception] = {
    case sql: SQLException if sql.getMessage.contains("duplicate key value") =>
      new JurnalDuplicateKeyException("Key duplicated", sql)
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