/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 14:45
 */

package org.s4s0l.betelgeuse.akkacommons.test

import com.typesafe.config.Config
import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService
import org.s4s0l.betelgeuse.akkacommons.persistence.BetelgeuseAkkaPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BetelgeuseAkkaPersistenceCrate
import scalikejdbc.DBSession

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaTestWithCrateDb[T <: BetelgeuseAkkaPersistenceCrate] extends BetelgeuseAkkaTestWithPersistence[T] {

  override def isCleanupOn: Boolean = true

  override def cleanUp(dbName: String, cfg: Config)(implicit session: DBSession): Unit = {
    val schemasString = cfg.getString(s"db.$dbName.flyway.schemas")
    val schemas = StringUtils.tokenizeToStringArray(schemasString, ",")
    schemas.foreach { it =>
      DbCrateTest.cleanUp(it)(session)
    }
  }

  def refreshTable(tableName: String): Unit = {
    val extension = BetelgeuseAkkaPersistenceExtension.get(system)
    extension.update { implicit session =>
      DbCrateTest.refreshTable(tableName, extension.defaultSchemaName)
    }

  }
}
