package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.persistence.BetelgeuseAkkaPersistence

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaPersistenceCrate extends BetelgeuseAkkaPersistence {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaPersistenceCrate])

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with persistence-crate.conf with fallback to...")
    loadResourceWithPlaceholders("persistence-datasource-crate.conf-template", Map(
      "datasource" -> dataSourceName,
      "address" -> (if (serviceInfo.docker) s"${systemName}_db" else "127.0.0.1")   ))
      .withFallback(ConfigFactory.parseResources("persistence-crate.conf"))
      .withFallback(super.customizeConfiguration)
  }


}
