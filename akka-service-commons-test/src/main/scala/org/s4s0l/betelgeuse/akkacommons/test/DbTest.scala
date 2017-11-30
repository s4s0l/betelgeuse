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

package org.s4s0l.betelgeuse.akkacommons.test

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.{BetelgeuseDb, BetelgeuseEntityObject}
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.{BeforeAndAfterAll, Suite}
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
trait DbTest extends BeforeAndAfterAll {
  this: Suite =>

  var db: BetelgeuseDb = _

  def cleanUp(configUsedInCleanup: Config)(implicit session: DBSession): Unit = {}

  def isCleanupOn: Boolean = false

  def DatabaseName: String = getClass.getSimpleName

  def SchemaName: String = getClass.getSimpleName.toLowerCase

  final def sqlExecution[A](execution: DBSession => A): A = {
    NamedDB(Symbol(DatabaseName)) localTx { implicit session =>
      BetelgeuseEntityObject.runWithSchemaAndPool(DatabaseName, SchemaName) {
        execution(session)
      }
    }
  }

  protected def loadFallbackConfig(): Config = {
    AllUtils.placeholderResourceConfig("DbTest.conf-template",
      Map(
        "testName" -> DatabaseName,
        "schemaName" -> SchemaName
      ))
  }

  private def loadConfig(): Config = {
    ConfigFactory.load(s"$DatabaseName.conf")
  }


  override protected def beforeAll(): Unit = {
    val config = loadConfig().withFallback(loadFallbackConfig())
    if (isCleanupOn)
      DbTest.runWithoutSchemaMigration(config, DatabaseName, (c, x) => cleanUp(c)(x))
    db = new BetelgeuseDb(config)
    db.loadGlobalSettings()
    db.setup(Symbol(DatabaseName))
  }

  override protected def afterAll(): Unit = {
    db.closeAll()
  }

}


object DbTest {

  def runWithoutSchemaMigration(config: Config, databaseName: String, cleanUp: (Config, DBSession) => Unit): Unit = {
    val prepareConfig = ConfigFactory.parseString(
      s"""
         |flyway.enabled = false
         |db.$databaseName.flyway.enabled = false""".stripMargin)
    val usedConfig = prepareConfig.withFallback(config)
    val prepareConnection = new BetelgeuseDb(usedConfig)
    try {
      prepareConnection.loadGlobalSettings()
      prepareConnection.setup(Symbol(databaseName))
      NamedDB(Symbol(databaseName)) localTx { implicit session =>
        cleanUp(usedConfig, session)
      }
    } finally {
      prepareConnection.close(Symbol(databaseName))
    }
  }

}