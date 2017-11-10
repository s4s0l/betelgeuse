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

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaClusteringClient extends BetelgeuseAkkaService {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaClusteringClient])
  private lazy val vipDockerMode = if (System.getProperty("akka.cluster.dns.vipMode", "false") == "true") "tasks." else ""

  protected def clusteringClientCreateConfig(serviceName: String, servicePortBase: Int): Config = {
    //todo some naming provider would be usefull for cluster dns also
    val port = s"1${"%02d%02d".format(servicePortBase, 1)}"
    val address = if (serviceInfo.docker)
      s"dns.$vipDockerMode${serviceName}_service"
    else
      s"127.0.0.1"
    val cfg =
      s"""
         |akka.cluster.clients {
         |  $serviceName {
         |    initial-contacts = ["akka.tcp://$serviceName@$address:$port/system/receptionist"]
         |  }
         |}
     """.stripMargin
    ConfigFactory.parseString(cfg)
  }

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with clustering-client.conf with fallback to...")
    ConfigFactory.parseResources("clustering-client.conf").withFallback(super.customizeConfiguration)
  }


  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BetelgeuseAkkaClusteringClientExtension)
    LOGGER.info("Initializing done.")
  }


  def clusteringClientExtension: BetelgeuseAkkaClusteringClientExtension = BetelgeuseAkkaClusteringClientExtension.get(system)

}



