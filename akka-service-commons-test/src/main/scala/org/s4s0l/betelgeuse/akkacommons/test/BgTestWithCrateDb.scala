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
