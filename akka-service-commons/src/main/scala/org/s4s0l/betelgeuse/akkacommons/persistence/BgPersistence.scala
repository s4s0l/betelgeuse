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

package org.s4s0l.betelgeuse.akkacommons.persistence


import akka.Done
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
trait BgPersistence extends BgService {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgPersistence])

  protected def flytrackLocations(): Seq[String] = {
    Seq(s"db/migration/$dataSourceName")
  }

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with persistence.conf with fallback to...")
    loadResourceWithPlaceholders("persistence-datasource.conf-template", Map(
      "datasource" -> dataSourceName,
      "locations" -> flytrackLocations().mkString(","),
      "schema" -> dataSourceSchema))
      .withFallback(ConfigFactory.parseResources("persistence.conf"))
      .withFallback(super.customizeConfiguration)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BgPersistenceExtension)
    shutdownCoordinated(
      "cluster-exiting", "persistence-datasources-close") { () =>
      Future {
        persistenceExtension.closeDb()
        Done
      }
    }
    LOGGER.info("Initializing done.")
  }


  implicit def persistenceExtension: BgPersistenceExtension = BgPersistenceExtension(system)

  implicit def dbAccess: DbAccess = BgPersistenceExtension(system).dbAccess

  protected def dataSourceName: String = systemName

  protected def dataSourceSchema: String = systemName.toLowerCase.replace("-", "_")


  protected def loadResourceWithPlaceholders(resourceName: String, params: Map[String, String]): Config = {
    AllUtils.placeholderResourceConfig(resourceName, params)
  }

}