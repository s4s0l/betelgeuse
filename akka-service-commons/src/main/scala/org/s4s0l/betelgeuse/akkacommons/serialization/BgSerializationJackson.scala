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



package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.serialization.SerializationExtension
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BgService

/**
  * @author Marcin Wielgus
  */
trait BgSerializationJackson extends BgService {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgSerializationJackson])




  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with serialization-jackson.conf with fallback to...")
    ConfigFactory.parseResources("serialization-jackson.conf").withFallback(super.customizeConfiguration)
  }

  implicit def serializationJacksonExtension: BgSerializationJacksonExtension =
    BgSerializationJacksonExtension.get(system)

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    SerializationExtension.get(system)
    system.registerExtension(BgSerializationJacksonExtension)
    LOGGER.info("Initializing done.")
  }
}
