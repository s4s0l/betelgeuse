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

import org.flywaydb.core.internal.dbsupport.{DbSupport, JdbcTemplate, SqlStatementBuilder}
import org.flywaydb.core.internal.util.StringUtils
import org.slf4j.{Logger, LoggerFactory}

/**
  * @author Marcin Wielgus
  */
class CrateDbSupport(jdbcTemplate: JdbcTemplate) extends DbSupport(jdbcTemplate) {

  val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  override def getCurrentUserFunction: String = "'default_user'"

  override def doGetCurrentSchemaName(): String =
    jdbcTemplate.queryForString("SELECT CURRENT_SCHEMA")

  override def supportsDdlTransactions(): Boolean = false

  override def getBooleanTrue: String = "true"

  override def getDbName: String = "crate"

  override def doChangeCurrentSchemaTo(schema: String): Unit = {
    if (!StringUtils.hasLength(schema))
      jdbcTemplate.getConnection.setSchema("doc")
    else
      jdbcTemplate.getConnection.setSchema(schema)
  }

  override def getSchema(name: String): CrateDbSchema = {
    new CrateDbSchema(jdbcTemplate, this, name)
  }

  override def catalogIsSchema() = false

  override def createSqlStatementBuilder() = new SqlStatementBuilder()

  override def doQuote(identifier: String): String = "\"" + StringUtils.replaceAll(identifier, "\"", "\"\"") + "\""

  override def getBooleanFalse = "false"

  override def useSingleConnection() = true
}
