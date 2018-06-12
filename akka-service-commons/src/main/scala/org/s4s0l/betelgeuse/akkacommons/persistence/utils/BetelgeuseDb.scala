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


package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import akka.actor.Scheduler
import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSupport.TxExecutor
import org.s4s0l.betelgeuse.akkacommons.persistence.versioning.FlyTrackPersistenceSchemaUpdater
import org.s4s0l.betelgeuse.utils.AllUtils
import org.slf4j.LoggerFactory
import scalikejdbc.config._
import scalikejdbc.{DBSession, GlobalSettings, NamedDB, SettingsProvider}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BetelgeuseDb(val config: Config)(implicit executor: ExecutionContext, scheduler: Scheduler)
  extends DBs
    with TypesafeConfigReader
    with TypesafeConfig
    with NoEnvPrefix {

  private val encounteredPoolLocks = mutable.Map[String, DbLocksSupport]()
  private val LOGGER = LoggerFactory.getLogger(getClass)

  def readOnly[A](execution: DBSession => A, name: String = getDefaultPoolName.get,
                  settingsProvider: SettingsProvider = SettingsProvider.default): A = {
    underlyingPureScalikeJdbcDb(name, settingsProvider).readOnly {
      implicit session =>
        val customizer = encounteredPoolsAndSchemas(name)._2
        val schema = customizer.onReadOnly(session)(getDefaultSchemaNameFromPoolName(name))
        BetelgeuseEntityObject.runWithSchemaAndPool(name, schema) {
          execution(session)
        }
    }
  }

  def localTx[A](execution: DBSession => A, name: String = getDefaultPoolName.get,
                 settingsProvider: SettingsProvider = SettingsProvider.default): A = {
    underlyingPureScalikeJdbcDb(name, settingsProvider).localTx {
      implicit session =>
        val customizer = encounteredPoolsAndSchemas(name)._2
        val schema = customizer.onLocalTx(session)(getDefaultSchemaNameFromPoolName(name))
        BetelgeuseEntityObject.runWithSchemaAndPool(name, schema) {
          execution(session)
        }
    }
  }

  val localTxExecutor: TxExecutor = new TxExecutor {
    override def doInTx[T](code: DBSession => T): T = {
      localTx { implicit session =>
        code(session)
      }
    }
  }

  def getLocks(name: String = getDefaultPoolName.get): DbLocks = new DbLocks(encounteredPoolLocks(name), localTxExecutor)

  override def setup(dbName: Symbol): Unit = {
    super.setup(dbName)
    val dbConfig = config.getConfig(envPrefix + "db." + dbName.name)
    val flywayConfig: Config = getFlywayConfig(dbName, dbConfig)
    val str = dbConfig.getString("sessionCustomizer")
    val customizer = Class.forName(str).newInstance().asInstanceOf[SessionCustomizer]

    if (flywayConfig.hasPath("schemas")) {
      val schemasString = flywayConfig.getString("schemas")
      val schemas = StringUtils.tokenizeToStringArray(schemasString, ",")
      encounteredPoolAndSchemas(dbName.name, schemas, customizer)
    } else {
      LOGGER.warn(s"Flyway config has no schemas defined for database ${dbName.name}")
    }

    import org.s4s0l.betelgeuse.utils.AllUtils._
    val locksSupport = dbConfig
      .string("locksProvider")
      .map { locksProviderClassName =>
        Class.forName(locksProviderClassName).getConstructor(classOf[Config]).newInstance(config).asInstanceOf[DbLocksSupport]
      }.getOrElse {
      LOGGER.warn(s"No locks provided for database ${dbName.name}!")
      DbLocksSupport.noOpLocker
    }
    encounteredPoolLocks += (dbName.name -> locksSupport)
    locksSupport.initLocks(localTxExecutor)


    val migrationsEnabled = dbConfig.hasPath("migrations.enabled") && dbConfig.getBoolean("migrations.enabled")

    if (migrationsEnabled) {
      import AllUtils._
      val lockDuration = dbConfig.duration("migration.lockDuration").getOrElse(1 minute)
      val lockAttemptCount = dbConfig.int("migration.lockAttemptCount").getOrElse(35)
      val lockAttemptInterval = dbConfig.duration("migration.lockAttemptInterval").getOrElse(2 second)
      val preLockFinishProlong = dbConfig.duration("migration.preLockFinishProlong").getOrElse(500 millis)

      locksSupport.runLocked(s"FlywayMigration", localTxExecutor, DbLocksSettings.DbLocksRolling(lockDuration, lockAttemptCount, lockAttemptInterval, preLockFinishProlong)) { implicit session =>
        new FlyTrackPersistenceSchemaUpdater(flywayConfig).updateSchema(new DummyDataSource(dbName))
      }
    }
  }

  private def underlyingPureScalikeJdbcDb(name: String = getDefaultPoolName.get,
                                          settingsProvider: SettingsProvider = SettingsProvider.default): NamedDB = {
    NamedDB(Symbol(name), settingsProvider)
  }

  private def getFlywayConfig(dbName: Symbol, dbConfig: Config) = {
    import org.s4s0l.betelgeuse.utils.AllUtils._

    val defaultConfig: Config = getFlywayDefaultConfig(dbName)

    dbConfig.config("flyway").map { it =>
      it.withFallback(defaultConfig)
    }.getOrElse(defaultConfig)
  }

  private def getFlywayDefaultConfig(dbName: Symbol) = {
    val locationsConfig = ConfigFactory.parseString(
      s"""
         |locations = db/migration/${dbName.name}
         |schemas = ${dbName.name.toLowerCase}
         |""".stripMargin)
    val defaultConfig = if (config.hasPath("flyway")) {
      config.getConfig("flyway").withFallback(locationsConfig)
    } else {
      locationsConfig
    }
    defaultConfig
  }

  override def loadGlobalSettings(): Unit = {
    super.loadGlobalSettings()
    switchJtaSourceCompatibleDefault()
  }

  private def switchJtaSourceCompatibleDefault(): Unit = {
    GlobalSettings.jtaDataSourceCompatible = true
    for {
      globalConfig <- readConfig(config, envPrefix + "scalikejdbc.global")
    } {
      GlobalSettings.jtaDataSourceCompatible = readBoolean(globalConfig, "jtaDataSourceCompatible").getOrElse(true)
      GlobalSettings.loggingSQLErrors = readBoolean(globalConfig, "loggingSQLErrors").getOrElse(true)

    }
  }

  private def readConfig(config: Config, path: String): Option[Config] = {
    if (config.hasPath(path)) Some(config.getConfig(path)) else None
  }

  private def readBoolean(config: Config, path: String): Option[Boolean] = {
    if (config.hasPath(path)) Some(config.getBoolean(path)) else None
  }

  def getDefaultSchemaNameFromPoolName(poolName: String): Option[String] = {
    encounteredPoolsAndSchemas.synchronized {
      Some(poolName)
        .map(it => encounteredPoolsAndSchemas(it)._1)
        .filter(it => it.lengthCompare(1) == 0)
        .map(it => it.head)
        .orElse {
          LOGGER.warn(s"Multiple pool names/schemas found ($encounteredPoolsAndSchemas), unable to reasonably select default schema - override schemaName in table classes")
          None
        }
    }
  }

  override def closeAll(): Unit = {
    dbNames.foreach { dbName => close(Symbol(dbName)) }
    encounteredPoolReleaseAll(dbNames)
  }

  private val encounteredPoolsAndSchemas = mutable.Map[String, (Seq[String], SessionCustomizer)]()

  private def encounteredPoolReleased(poolName: String): Unit = {
    encounteredPoolsAndSchemas.synchronized {
      encounteredPoolsAndSchemas -= poolName
    }
  }

  private def encounteredPoolReleaseAll(dbs: List[String]): Unit = {
    encounteredPoolsAndSchemas.synchronized {
      dbs.foreach(it => encounteredPoolsAndSchemas -= it)
    }
  }

  private def encounteredPoolAndSchemas(poolName: String, schemas: Seq[String], sessionCustomizer: SessionCustomizer) = {
    encounteredPoolsAndSchemas.synchronized {
      if (encounteredPoolsAndSchemas.contains(poolName)) {
        encounteredPoolsAndSchemas += (poolName -> (encounteredPoolsAndSchemas(poolName)._1 ++ schemas, sessionCustomizer))
      } else {

        encounteredPoolsAndSchemas += (poolName -> (schemas, sessionCustomizer))
      }
    }
  }

  def getDefaultPoolName: Option[String] = {
    encounteredPoolsAndSchemas.synchronized {
      if (encounteredPoolsAndSchemas.size != 1) {
        LOGGER.warn(s"Multiple pool names found ($encounteredPoolsAndSchemas) , unable to reasonably select one - override connectionPoolName in table classes")
        None
      } else {
        Some(encounteredPoolsAndSchemas.toSeq.head._1)
      }
    }
  }

  override def close(dbName: Symbol): Unit = {
    DBs.close(dbName)
    encounteredPoolReleased(dbName.name)
  }

}

