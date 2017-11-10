/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
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
