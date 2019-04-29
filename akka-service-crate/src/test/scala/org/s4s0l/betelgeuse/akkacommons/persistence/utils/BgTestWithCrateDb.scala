/*
 * Copyright© 2019 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

;

/**
  * @author Marcin Wielgus
  */

import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BgPersistenceCrate
import org.s4s0l.betelgeuse.akkacommons.test.{BgTestWithPersistence, DbCrateTest};

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
