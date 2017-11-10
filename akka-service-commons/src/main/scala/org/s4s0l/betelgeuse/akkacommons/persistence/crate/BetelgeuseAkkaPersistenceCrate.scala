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
