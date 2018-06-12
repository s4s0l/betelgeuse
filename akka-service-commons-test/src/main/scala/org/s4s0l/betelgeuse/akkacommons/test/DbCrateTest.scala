/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

package org.s4s0l.betelgeuse.akkacommons.test

import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb
import org.s4s0l.betelgeuse.utils
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.Suite
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax

/**
  * @author Marcin Wielgus
  */
trait DbCrateTest extends DbTest {
  this: Suite =>

  override protected def loadFallbackConfig(): Config = {
    utils.AllUtils.placeholderResourceConfig("DbCrateTest.conf-template", Map(
      "testName" -> DatabaseName
    )).withFallback(super.loadFallbackConfig())
  }

  def refreshTable(tableName: String, schemaName: String = SchemaName)(implicit session: DBSession): Unit =
    DbCrateTest.refreshTable(tableName, schemaName)(session)

  override def cleanUp(db: BetelgeuseDb): Unit =
    db.localTx { implicit session =>
      DbCrateTest.cleanUp(SchemaName)(session)
    }

  override def isCleanupOn: Boolean = true

  final def deleteAllRecords(tableName: String, schemaName: String = SchemaName)(implicit session: DBSession): Unit =
    DbCrateTest.deleteAllRecords(tableName, schemaName)(session)

  final def deleteAllTablesInSchema(schemaName: String = SchemaName)(implicit session: DBSession): Unit =
    DbCrateTest.deleteAllTablesInSchema(schemaName)(session)
}

object DbCrateTest {

  def refreshTable(tableName: String, schemaName: String)(implicit session: DBSession): Unit = {
    val schema = SQLSyntax.createUnsafely(schemaName)
    val table = SQLSyntax.createUnsafely(tableName)
    sql"refresh table $schema.$table".execute().apply()
  }

  def cleanUp(schemaName: String)(implicit session: DBSession): Unit = {
    deleteAllTablesInSchema(schemaName)(session)
    AllUtils.tryNTimes("DbCrateTestCleanUp", 2) {
      //see https://github.com/crate/crate-jdbc/issues/237
      deleteAllRecords("locks", "locks")(session)
    }
  }


  final def deleteAllRecords(tableName: String, schemaName: String)(implicit session: DBSession): Unit = {
    sql"select TABLE_NAME from information_schema.tables where table_schema=$schemaName and table_name=$tableName"
      .map(_.string(1)).first().apply()
      .foreach { it =>
        val schema = SQLSyntax.createUnsafely(schemaName)
        val table = SQLSyntax.createUnsafely(it)
        sql"delete from $schema.$table".update().apply()
        sql"refresh table $schema.$table".update().apply()
      }
  }

  final def deleteAllTablesInSchema(schemaName: String)(implicit session: DBSession): Unit = {
    val tablesToDelete = sql"select TABLE_NAME from information_schema.tables where table_schema=$schemaName"
      .map(_.string(1)).list().apply()
    val schema = SQLSyntax.createUnsafely(schemaName)
    tablesToDelete.foreach { it =>
      val table = SQLSyntax.createUnsafely(it)
      sql"drop table $schema.$table".execute().apply()
    }
  }
}