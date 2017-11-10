/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-16 02:12
 *
 */

package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.serialization.SerializationExtension
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaSerializationJackson extends BetelgeuseAkkaService{

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaSerializationJackson])




  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with serialization-jackson.conf with fallback to...")
    ConfigFactory.parseResources("serialization-jackson.conf").withFallback(super.customizeConfiguration)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    SerializationExtension.get(system)
    system.registerExtension(BetelgeuseAkkaSerializationJacksonExtension)
    LOGGER.info("Initializing done.")
  }

  def serializationJacksonExtension: BetelgeuseAkkaSerializationJacksonExtension =
    BetelgeuseAkkaSerializationJacksonExtension.get(system)
}
