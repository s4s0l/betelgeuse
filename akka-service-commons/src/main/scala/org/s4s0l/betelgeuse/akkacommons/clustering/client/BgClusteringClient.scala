/*
 * Copyright© 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.{BgService, BgServiceId}

/**
  * @author Marcin Wielgus
  */
trait BgClusteringClient extends BgService {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgClusteringClient])
  private lazy val vipDockerMode = if (System.getProperty("akka.cluster.dns.vipMode", "false") == "true") "tasks." else ""

  implicit def clusteringClientExtension: BgClusteringClientExtension = BgClusteringClientExtension.get(system)

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with clustering-client.conf with fallback to...")
    ConfigFactory.parseResources("clustering-client.conf").withFallback(super.customizeConfiguration)
  }

  protected def clusteringClientCreateConfig(id: BgServiceId): Config = {
    //todo some naming provider would be usefull for cluster dns also
    val port = s"1${"%02d%02d".format(id.portBase, 1)}"
    val address = if (serviceInfo.docker)
      s"dns.$vipDockerMode${id.systemName}_service"
    else
      s"127.0.0.1"
    val cfg =
      s"""
         |akka.cluster.clients {
         |  ${id.systemName} {
         |    initial-contacts = ["akka.tcp://${id.systemName}@$address:$port/system/receptionist"]
         |  }
         |}
     """.stripMargin
    ConfigFactory.parseString(cfg)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BgClusteringClientExtension)
    LOGGER.info("Initializing done.")
  }

}



