package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.sql.SQLException

import akka.serialization.SerializationExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, ScalikeAsyncWriteJournal}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession

/**
  * @author Marcin Wielgus
  */
class CrateAsyncWriteJournal extends ScalikeAsyncWriteJournal[CrateAsyncWriteJournalEntity]() {
  private val LOGGER = LoggerFactory.getLogger(getClass)
  override val dao: CrateAsyncWriteJournalDao = new CrateAsyncWriteJournalDao(JacksonJsonSerializer.get(serialization))


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
