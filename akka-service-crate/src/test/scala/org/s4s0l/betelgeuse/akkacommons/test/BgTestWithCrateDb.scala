package org.s4s0l.betelgeuse.akkacommons.test

import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BgPersistenceCrate
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb

/**
  * @author Marcin Wielgus
  */
@deprecated("Please migrate to BgTestCrate", "?")
trait BgTestWithCrateDb[T <: BgPersistenceCrate] extends BgTestWithPersistence[T] {

  override def isCleanupOn: Boolean = true

  override def cleanUp(db: BetelgeuseDb): Unit = {
    val schemasString = db.config.getString(s"db.${db.getDefaultPoolName.get}.flyway.schemas")
    val schemas = StringUtils.tokenizeToStringArray(schemasString, ",")
    schemas.foreach { it =>
      db.localTx { session =>
        DbCrateTest.cleanUp(it)(session)
      }
    }
  }

  def refreshTable(tableName: String): Unit = {
    val extension = BgPersistenceExtension.get(system)
    extension.dbAccess.update { implicit session =>
      DbCrateTest.refreshTable(tableName, extension.defaultSchemaName)
    }

  }
}
