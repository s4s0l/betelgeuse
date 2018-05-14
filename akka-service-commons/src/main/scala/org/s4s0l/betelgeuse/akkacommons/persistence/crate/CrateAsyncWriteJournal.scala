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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.sql.SQLException

import akka.serialization.{Serialization, SerializationExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, ScalikeAsyncWriteJournal}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
class CrateAsyncWriteJournal()
  extends ScalikeAsyncWriteJournal[CrateAsyncWriteJournalEntity]() {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  private val serialization: Serialization = SerializationExtension.get(context.system)
  override val dao: CrateAsyncWriteJournalDao =
    new CrateAsyncWriteJournalDao(serialization, JacksonJsonSerializer.get(serialization))

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future.failed(new UnsupportedOperationException("message deletion is not supported, it could mess up consistency."))
  }

  override def mapExceptions(session: DBSession): PartialFunction[Exception, Exception] = {
    case sql: SQLException if sql.getMessage.contains("DuplicateKeyException") =>
      try {
        //TODO some strategy at most once in a second or something
        dao.refreshTable(session)
      } catch {
        case _: Exception =>
          LOGGER.error("Unable to refresh table on duplicate key exception")
      }
      new JurnalDuplicateKeyException("Key duplicated", sql)
  }
}
