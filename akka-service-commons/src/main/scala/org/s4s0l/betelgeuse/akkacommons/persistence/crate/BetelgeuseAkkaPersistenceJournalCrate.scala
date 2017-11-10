package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.serialization.SerializationExtension
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.utils.AllUtils

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaPersistenceJournalCrate extends BetelgeuseAkkaPersistenceCrate {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaPersistenceJournalCrate])

  override protected def flytrackLocations(): Seq[String] = {
    super.flytrackLocations() :+ "db/migration/BetelgeuseAkkaPersistenceJournalCrate"
  }

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with persistence-journal-crate.conf with fallback to...")
    ConfigFactory.parseResources("persistence-journal-crate.conf").withFallback(super.customizeConfiguration)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    AllUtils.scalaWarmUpUniverseMirror()
    SerializationExtension.get(system)
    LOGGER.info("Getting serialization done")
    akka.persistence.Persistence.get(system)
    LOGGER.info("Initializing done.")
  }
}
