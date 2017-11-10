/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.flywaydb.core.internal.dbsupport.{JdbcTemplate, Table}

/**
  * @author Marcin Wielgus
  */
class CrateDbTable(jdbcTemplate: JdbcTemplate, dbSupport: CrateDbSupport, schema: CrateDbSchema, name: String)
  extends Table(jdbcTemplate, dbSupport, schema, name) {
  override def doExists(): Boolean = {
    jdbcTemplate.queryForStringList("select table_name from information_schema.tables where table_schema = ? and table_name = ?", schema.getName, name).size() == 1
  }

  override def doLock(): Unit = {
  }

  override def doDrop(): Unit = {
    jdbcTemplate.execute(s"drop table IF EXISTS ${schema.getName}.$name")
  }
}
