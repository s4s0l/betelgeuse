/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.flywaydb.core.internal.dbsupport.{JdbcTemplate, Schema, Table}

import scala.collection.JavaConverters._

/**
  * @author Marcin Wielgus
  */
class CrateDbSchema(jdbcTemplate: JdbcTemplate, dbSupport: CrateDbSupport, name: String)
  extends Schema[CrateDbSupport](jdbcTemplate, dbSupport, name) {
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
