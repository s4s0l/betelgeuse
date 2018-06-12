/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

import com.typesafe.config.Config
import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb
import org.s4s0l.betelgeuse.utils
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.Suite
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax

/**
  * @author Maciej Flak
  */
trait DbRoachTest extends DbTest {
  this: Suite =>

  override protected def loadFallbackConfig(): Config = {
    utils.AllUtils.placeholderResourceConfig("DbRoachTest.conf-template", Map(
      "testName" -> DatabaseName,
      "schemaName" -> SchemaName
    )).withFallback(super.loadFallbackConfig())
  }

  override def cleanUp(db: BetelgeuseDb): Unit =
    DbRoachTest.cleanUp(SchemaName)(db)

  override def isCleanupOn: Boolean = true

  final def deleteAllRecords(tableName: String, schemaName: String = SchemaName)(implicit session: DBSession): Unit =
    DbRoachTest.deleteAllRecords(tableName, schemaName)(session)

  final def deleteAllTablesInSchema(schemaName: String = SchemaName)(implicit session: DBSession): Unit =
    DbRoachTest.deleteAllTablesInSchema(schemaName)(session)
}

object DbRoachTest {

  def cleanEverything(config: Config): Unit = {
    import scala.collection.JavaConverters._
    val dbs = config.getConfig("db").root.keySet.asScala.toList
    dbs.foreach { it =>
      DbTest.runWithoutSchemaMigration(config, it, { db =>
        val schemasString = db.config.getString(s"db.${db.getDefaultPoolName.get}.flyway.schemas")
        val schemas = StringUtils.tokenizeToStringArray(schemasString, ",")
        schemas.foreach { it =>
          DbRoachTest.cleanUp(it)(db)
        }
      })
    }
  }

  def cleanUp(schemaName: String)(db: BetelgeuseDb): Unit = {
    AllUtils.tryNTimes("DbRoachTestCleanupLocksDelete", 4) {
      db.localTx { implicit session =>
        deleteAllRecords("locks", "locks")(session)
      }
    }
    AllUtils.tryNTimes("DbRoachTestCleanupDbDrop", 4) {
      db.localTx { implicit session =>
        dropDatabase(schemaName)(session)
      }
    }
  }


  final def deleteAllRecords(tableName: String, schemaName: String)(implicit session: DBSession): Unit = {
    val unsafeSchema = SQLSyntax.createUnsafely(schemaName)
    val unsafeTable = SQLSyntax.createUnsafely(tableName)
    if (
      sql"""show databases""".map(_.string(1)).list.apply().contains(schemaName)) {
      if (sql"show tables from $unsafeSchema".map(_.string(1)).list.apply().contains(tableName)) {
        sql"delete from $unsafeSchema.$unsafeTable".update().apply()
      }
    }
  }

  final def dropDatabase(schemaName: String)(implicit session: DBSession): Unit = {
    val schema = SQLSyntax.createUnsafely(schemaName)
    sql"drop database if EXISTS $schema".execute().apply()
  }

  final def deleteAllTablesInSchema(schemaName: String)(implicit session: DBSession): Unit = {
    if (
      sql"""show databases""".map(_.string(1)).list.apply().contains(schemaName)) {
      val unsafeSchema = SQLSyntax.createUnsafely(schemaName)
      val tablesToDelete = sql"show tables from $unsafeSchema"
        .map(_.string(1)).list().apply()
      val schema = SQLSyntax.createUnsafely(schemaName)
      tablesToDelete.foreach { it =>
        val table = SQLSyntax.createUnsafely(it)
        sql"drop table $schema.$table".execute().apply()
      }
    }
  }
}