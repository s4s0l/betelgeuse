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



package org.s4s0l.betelgeuse.akkacommons.persistence.versioning

import javax.sql.DataSource

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.s4s0l.betelgeuse.utils.AllUtils._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * @author Marcin Wielgus
  */
class FlyTrackPersistenceSchemaUpdater(config: Config) extends PersistenceSchemaUpdater {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  private val ft = new Flyway()

  private val maps = mutable.Map[String, String]() //has to be mutable cause flyway is callin remove on it for some reason
  config.entrySet().forEach(it =>
    maps("flyway." + it.getKey) = config.getString(it.getKey)
  )
  ft.configure(maps.asJava)


  private val retries = if (config.hasPath("retries")) config.getInt("retries") else 1
  private val retriesDelay = if (config.hasPath("retriesDelay")) config.getInt("retriesDelay") else 3000
  private val connectionAcquireRetries = if (config.hasPath("connectionAcquireRetries")) config.getInt("connectionAcquireRetries") else 60
  private val connectionAcquireDelay = if (config.hasPath("connectionAcquireDelay")) config.getInt("connectionAcquireDelay") else 1000
  private val connectionAcquireValidationQuery = if (config.hasPath("connectionAcquireValidationQuery")) config.getString("connectionAcquireValidationQuery") else "select 1"


  private def validateConnectionOnce(dataSource: DataSource): Boolean =
    tryWithAutoCloseableWithException(dataSource.getConnection) {
      conn =>
        tryWithAutoCloseableWithException(conn.prepareStatement(connectionAcquireValidationQuery)) {
          ps =>
            tryWithAutoCloseableWithException(ps.executeQuery()) {
              rs =>
                if (rs.next()) {
                  LOGGER.info("Connection acquired.")
                  true
                } else {
                  false
                }
            }
        }
    }

  private def validateConnection(dataSource: DataSource): Unit =
    tryNTimesMessage(connectionAcquireRetries,
      "Before schema update connection validation failed",
      Set(classOf[Exception]), connectionAcquireDelay) {
      validateConnectionOnce(dataSource)
    }

  override def updateSchema(dataSource: DataSource): Unit = {
    validateConnection(dataSource)
    tryNTimesMessage(retries, "Schema update process failed",
      Set(classOf[Exception]), retriesDelay) {
      tryMigrate(dataSource)
    }
  }

  def tryMigrate(dataSource: DataSource): Unit = {
    ft.setDataSource(dataSource)
    ft.migrate()
    LOGGER.info("Migration Done!!!")
  }

}
