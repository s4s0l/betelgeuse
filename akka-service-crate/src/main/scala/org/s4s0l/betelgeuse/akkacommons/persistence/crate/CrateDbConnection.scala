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


package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.sql.Types

import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.internal.database.Connection
import org.flywaydb.core.internal.util.StringUtils
import org.flywaydb.core.internal.util.jdbc.JdbcTemplate


/**
  * @author Maciej Flak
  */
class CrateDbConnection(conf: Configuration, db: CrateDatabase, jdbcTemplate: JdbcTemplate, nullType: Int = Types.NULL)
  extends Connection[CrateDatabase](conf, db, jdbcTemplate.getConnection, nullType) {

  override def getCurrentSchemaNameOrSearchPath: String = jdbcTemplate.queryForString("SELECT CURRENT_SCHEMA")



  override def doChangeCurrentSchemaOrSearchPathTo(schema: String): Unit = {
    if (!StringUtils.hasLength(schema))
      jdbcTemplate.getConnection.setSchema("doc")
    else
      jdbcTemplate.getConnection.setSchema(schema)
  }

  override def getSchema(name: String): CrateDbSchema = {
    new CrateDbSchema(jdbcTemplate, db, name)
  }
}
