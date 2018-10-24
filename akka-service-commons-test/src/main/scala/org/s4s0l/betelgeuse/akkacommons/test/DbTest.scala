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

package org.s4s0l.betelgeuse.akkacommons.test

import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSupport.TxExecutor
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.{BeforeAndAfterAll, Suite}
import scalikejdbc.DBSession

/**
  * @author Marcin Wielgus
  */
trait DbTest extends BeforeAndAfterAll {
  this: Suite =>

  var db: BetelgeuseDb = _

  final def localTx[A](execution: DBSession => A): A = {
    db localTx {
      implicit session =>
        execution(session)
    }
  }

  def SchemaName: String = getClass.getSimpleName.toLowerCase

  final def localTxExecutor: TxExecutor = db.localTxExecutor

  override protected def beforeAll(): Unit = {
    val config = loadConfig().withFallback(loadFallbackConfig())
    if (isCleanupOn)
      DbTest.runWithoutSchemaMigration(config, DatabaseName, cleanUp)
    db = new BetelgeuseDb(config)(scala.concurrent.ExecutionContext.Implicits.global, (new VirtualTime).scheduler)
    db.loadGlobalSettings()
    db.setup(Symbol(DatabaseName))
  }

  def isCleanupOn: Boolean = false

  def DatabaseName: String = getClass.getSimpleName

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

  def cleanUp(db: BetelgeuseDb): Unit = {}

  override protected def afterAll(): Unit = {
    db.closeAll()
  }

}


object DbTest {

  def runWithoutSchemaMigration(config: Config, databaseName: String, cleanUp: (BetelgeuseDb) => Unit): Unit = {
    val prepareConfig = ConfigFactory.parseString(
      s"""
         |db.$databaseName.migrations.enabled = false
         |db.$databaseName.locks.enabled = false
         """.stripMargin)
    val usedConfig = prepareConfig.withFallback(config)
    val prepareConnection = new BetelgeuseDb(usedConfig)(concurrent.ExecutionContext.Implicits.global, (new VirtualTime).scheduler) // migrations.enabled=false
    try {
      prepareConnection.loadGlobalSettings()
      prepareConnection.setup(Symbol(databaseName))
      cleanUp(prepareConnection)
    } finally {
      prepareConnection.close(Symbol(databaseName))
    }
  }

}