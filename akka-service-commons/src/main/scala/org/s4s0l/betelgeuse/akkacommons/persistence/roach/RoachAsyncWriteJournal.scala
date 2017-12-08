/*
 * Copyright© 2017 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import java.sql.SQLException

import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, PersistenceId, ScalikeAsyncWriteJournal}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Maciej Flak
  */
class RoachAsyncWriteJournal extends ScalikeAsyncWriteJournal[RoachAsyncWriteJournalEntity]() {
  private val LOGGER = LoggerFactory.getLogger(getClass)
  override val dao: RoachAsyncWriteJournalDao = new RoachAsyncWriteJournalDao(JacksonJsonSerializer.get(serialization))


  override def mapExceptions(session: DBSession): PartialFunction[Exception, Exception] = {
    case sql: SQLException if sql.getMessage.contains("duplicate key value") =>
      new JurnalDuplicateKeyException("Key duplicated", sql)
  }


  /**
    * asynchronously deletes all persistent messages up to `toSequenceNr`
    *
    * @param persistenceId
    * @param toSequenceNr
    * @return
    */
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long) : Future[Unit] = Future {
    dbAccess.query { implicit session =>
      val persId: PersistenceId = PersistenceId.fromString(persistenceId)
      dao.deleteUpTo(persId.tag, persId.uniqueId, toSequenceNr)
    }
  }


}