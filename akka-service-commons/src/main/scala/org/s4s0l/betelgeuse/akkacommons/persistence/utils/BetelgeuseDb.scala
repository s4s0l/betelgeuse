/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.persistence.versioning.FlyTrackPersistenceSchemaUpdater
import org.slf4j.LoggerFactory
import scalikejdbc.config._
import scalikejdbc.{ConnectionPool, DBSession, GlobalSettings, NamedDB, SettingsProvider}

import scala.collection.mutable
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BetelgeuseDb(val config: Config) extends DBs
  with TypesafeConfigReader
  with TypesafeConfig
  with NoEnvPrefix {

  private val encounteredPoolLocks = mutable.Map[String, DbLocksSupport]()
  private val LOGGER = LoggerFactory.getLogger(getClass)

  def underlyingPureScalikeJdbcDb(name: String = BetelgeuseDb.getDefaultPoolName.get,
                                  settingsProvider: SettingsProvider = SettingsProvider.default): NamedDB = {
    NamedDB(Symbol(name), settingsProvider)
  }

  def readOnly[A](execution: DBSession => A, name: String = BetelgeuseDb.getDefaultPoolName.get,
                  settingsProvider: SettingsProvider = SettingsProvider.default): A = {
    underlyingPureScalikeJdbcDb(name, settingsProvider).readOnly {
      implicit session =>
        BetelgeuseDb.getDefaultSchemaName match {
          case None => LOGGER.warn("No default scheme for read only session.")
          case Some(x) => session.connection.setSchema(x)
        }
        execution(session)
    }
  }

  def localTx[A](execution: DBSession => A, name: String = BetelgeuseDb.getDefaultPoolName.get,
                 settingsProvider: SettingsProvider = SettingsProvider.default): A = {
    underlyingPureScalikeJdbcDb(name, settingsProvider).localTx {
      implicit session =>
        BetelgeuseDb.getDefaultSchemaName match {
          case None => LOGGER.warn("No default scheme for read only session.")
          case Some(x) => session.connection.setSchema(x)
        }
        execution(session)
    }
  }

  def getLocks(name: String = BetelgeuseDb.getDefaultPoolName.get): DbLocksSupport = {
    encounteredPoolLocks(name)
  }

  override def setup(dbName: Symbol): Unit = {
    super.setup(dbName)
    val dbConfig = config.getConfig(envPrefix + "db." + dbName.name)
    val flywayConfig: Config = getFlywayConfig(dbName, dbConfig)

    if (flywayConfig.hasPath("schemas")) {
      val schemasString = flywayConfig.getString("schemas")
      val schemas = StringUtils.tokenizeToStringArray(schemasString, ",")
      BetelgeuseDb.encounteredPoolAndSchemas(dbName.name, schemas)
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
      DbLocksSupport.noopLocker
    }
    encounteredPoolLocks += (dbName.name -> locksSupport)

    if (flywayConfig.hasPath("enabled") && flywayConfig.getBoolean("enabled")) {
      localTx {
        implicit session =>
          import scala.concurrent.duration._
          locksSupport.initLocks(session)
          locksSupport.runLocked(s"MigrationOf${dbName.name}", DbLocksSettings(10 minutes, 35, 1 seconds)) {
            new FlyTrackPersistenceSchemaUpdater(flywayConfig).updateSchema(new DummyDataSource(dbName))
          }
      }
    }
  }

  private def getFlywayConfig(dbName: Symbol, dbConfig: Config) = {
    import org.s4s0l.betelgeuse.utils.AllUtils._
    val validationDefault = dbConfig.string("poolValidationQuery").map { it =>
      ConfigFactory.parseString(s"connectionAcquireValidationQuery=$it")
    }.getOrElse(ConfigFactory.empty())

    val defaultConfig: Config = getFlywayDefaultConfig(dbName).withFallback(validationDefault)

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


  override def close(dbName: Symbol): Unit = {
    DBs.close(dbName)
    BetelgeuseDb.encounteredPoolReleased(dbName.name)
  }

  override def closeAll(): Unit = {
    dbNames.foreach { dbName => close(Symbol(dbName)) }
    BetelgeuseDb.encounteredPoolReleaseAll(dbNames)
  }

}

object BetelgeuseDb {
  private val LOGGER = LoggerFactory.getLogger(getClass)
  private val encounteredPoolsAndSchemas = mutable.Map[String, Seq[String]]()

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

  private def encounteredPoolAndSchemas(poolName: String, schemas: Seq[String]) = {
    encounteredPoolsAndSchemas.synchronized {
      if (encounteredPoolsAndSchemas.contains(poolName)) {
        encounteredPoolsAndSchemas += (poolName -> (encounteredPoolsAndSchemas(poolName) ++ schemas))
      } else {

        encounteredPoolsAndSchemas += (poolName -> schemas)
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

  def getDefaultSchemaName: Option[String] = {
    encounteredPoolsAndSchemas.synchronized {
      getDefaultPoolName
        .map(it => encounteredPoolsAndSchemas(it))
        .filter(it => it.size == 1)
        .map(it => it.head)
        .orElse {
          LOGGER.warn(s"Multiple pool names/schemas found ($encounteredPoolsAndSchemas), unable to reasonably select default schema - override schemaName in table classes")
          None
        }
    }
  }

}