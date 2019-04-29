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


import java.sql

import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.internal.database._
import org.flywaydb.core.internal.util.jdbc.JdbcTemplate
import org.flywaydb.core.internal.util.scanner.LoadableResource
import org.flywaydb.core.internal.util.{PlaceholderReplacer, StringUtils}
import org.slf4j.{Logger, LoggerFactory}


/**
  * @author Marcin Wielgus
  */
class CrateDatabase(jdbcTemplate: JdbcTemplate, conf: Configuration)
  extends Database[CrateDbConnection](conf, jdbcTemplate.getConnection) {

  val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  override def getConnection(connection: sql.Connection) = new CrateDbConnection(conf, this, jdbcTemplate)

  // TODO check version or something?
  override def ensureSupported(): Unit = {}

  override def supportsDdlTransactions(): Boolean = false

  override def supportsChangingCurrentSchema(): Boolean = true

  override def getBooleanTrue: String = "true"

  override def getDbName: String = "crate"

  override def catalogIsSchema() = false

  override protected def doCreateSqlScript(resource: LoadableResource, placeholderReplacer: PlaceholderReplacer, mixed: Boolean) =
    new CrateSqlScript(resource, placeholderReplacer, mixed)

  override def doQuote(identifier: String): String = "\"" + StringUtils.replaceAll(identifier, "\"", "\"\"") + "\""

  override def getBooleanFalse = "false"

  override def useSingleConnection() = true

  override def getInsertStatement(table: Table): String = super.getInsertStatement(table)
}
