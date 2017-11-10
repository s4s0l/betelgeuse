/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.remoting

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService


trait BetelgeuseAkkaRemoting extends BetelgeuseAkkaService {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaRemoting])

  abstract override def customizeConfiguration:Config = {
    LOGGER.info("Customize config with remoting.conf as fallback to...")
    super.customizeConfiguration.withFallback(ConfigFactory.parseResources("remoting.conf"))
  }
}
