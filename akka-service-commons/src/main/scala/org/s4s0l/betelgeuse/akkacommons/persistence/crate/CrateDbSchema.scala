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



package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.flywaydb.core.internal.database.{Schema, Table}
import org.flywaydb.core.internal.util.jdbc.JdbcTemplate

import scala.collection.JavaConverters._

/**
  * @author Marcin Wielgus
  */
class CrateDbSchema(jdbcTemplate: JdbcTemplate, dbSupport: CrateDatabase, name: String)
  extends Schema[CrateDatabase](jdbcTemplate, dbSupport, name) {
  override def doClean(): Unit = {

    doAllTables().foreach(it => it.drop())
  }

  override def doAllTables(): Array[Table] = {
    jdbcTemplate.queryForStringList("select table_name from information_schema.tables where table_schema = ?", name)
      .asScala
      .map(it => getTable(it))
      .toArray
  }

  override def doExists(): Boolean = true

  override def doEmpty(): Boolean = doAllTables().length == 0

  override def doCreate(): Unit = {}

  override def getTable(tableName: String): CrateDbTable = new CrateDbTable(jdbcTemplate, dbSupport, this, tableName)

  override def doDrop(): Unit = doClean()
}
