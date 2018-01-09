/*
 * Copyright© 2017 the original author or authors.
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

import org.s4s0l.betelgeuse.akkacommons.persistence.utils.{BetelegeuseDbFlywayErrorTestBase, BetelgeuseDb}
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest

class BetelegeuseDbFlywayErrorTestCrate extends BetelegeuseDbFlywayErrorTestBase with DbCrateTest {


  override def refresh(db: BetelgeuseDb, table: String, schema: String): Unit = db.localTx { implicit session =>
    DbCrateTest.refreshTable(table, schema)
  }

  override def afterEach() {
    import scalikejdbc._
    LOGGER.info("calling AFTER ALL!")
    scalike.localTx { implicit session =>
      val tablesToDelete = sql"select TABLE_NAME from information_schema.tables where table_schema=${getClassName.toLowerCase}"
        .map(_.string(1)).list().apply()
      val schema = scalikejdbc.SQLSyntax.createUnsafely(getClassName.toLowerCase)
      tablesToDelete.foreach { it =>
        val table = scalikejdbc.SQLSyntax.createUnsafely(it)
        sql"drop table $schema.$table".execute().apply()
      }
    }
    scalike.closeAll()
  }
}