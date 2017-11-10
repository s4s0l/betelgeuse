/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-12 20:16
 *
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



