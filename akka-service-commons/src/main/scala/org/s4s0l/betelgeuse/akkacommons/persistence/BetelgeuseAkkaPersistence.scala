/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-06 01:13
 */

package org.s4s0l.betelgeuse.akkacommons.persistence


import akka.Done
import akka.actor.CoordinatedShutdown
import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.internal.util.PlaceholderReplacer
import org.flywaydb.core.internal.util.scanner.classpath.ClassPathResource
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaPersistence extends BetelgeuseAkkaService {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaPersistence])

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
    system.registerExtension(BetelgeuseAkkaPersistenceExtension)
    shutdownCoordinated(
      "cluster-exiting", "persistence-datasources-close") { () =>
      Future {
        persistenceExtension.closeDb()
        Done
      }
    }
    LOGGER.info("Initializing done.")
  }


  def persistenceExtension: BetelgeuseAkkaPersistenceExtension = BetelgeuseAkkaPersistenceExtension(system)

  def dataSourceName: String = systemName

  def dataSourceSchema: String = systemName.toLowerCase.replace("-", "_")


  protected def loadResourceWithPlaceholders(resourceName: String, params: Map[String, String]): Config = {
    AllUtils.placeholderResourceConfig(resourceName, params)
  }

}