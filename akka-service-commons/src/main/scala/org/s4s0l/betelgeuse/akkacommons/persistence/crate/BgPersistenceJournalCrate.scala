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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.serialization.SerializationExtension
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{BgPersistenceJournal, JournalReader}
import org.s4s0l.betelgeuse.utils.AllUtils

/**
  * @author Marcin Wielgus
  */
trait BgPersistenceJournalCrate extends BgPersistenceCrate with BgPersistenceJournal {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgPersistenceJournalCrate])

  override protected def flytrackLocations(): Seq[String] = {
    super.flytrackLocations() :+ "db/migration/BgPersistenceJournalCrate"
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

  implicit override final def journalReader: JournalReader = {
    new CrateJournalReader(dbAccess)
  }
}
