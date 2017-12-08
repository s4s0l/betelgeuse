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

import org.flywaydb.core.internal.database.{Database, Table}
import org.flywaydb.core.internal.util.jdbc.JdbcTemplate


/**
  * @author Marcin Wielgus
  */
class CrateDbTable(jdbcTemplate: JdbcTemplate, dbSupport: Database[CrateDbConnection], schema: CrateDbSchema, name: String)
  extends Table(jdbcTemplate, dbSupport, schema, name) {
  override def doExists(): Boolean = {
    jdbcTemplate.queryForStringList("select table_name from information_schema.tables where table_schema = ? and table_name = ?", schema.getName, name).size() == 1
  }

  /**
    * This is a workaround for crate being eventually consistent.
    * this is run before each migration DbMigrate.java:143
    */
  override def doLock(): Unit = {
    jdbcTemplate.update(s"refresh table ${schema.getName}.$name")
  }

  override def doDrop(): Unit = {
    jdbcTemplate.execute(s"drop table IF EXISTS ${schema.getName}.$name")
  }
}
