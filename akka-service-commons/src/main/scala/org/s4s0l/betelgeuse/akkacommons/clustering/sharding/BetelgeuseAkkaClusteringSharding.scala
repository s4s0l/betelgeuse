/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.sharding

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BetelgeuseAkkaClustering
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BetelgeuseAkkaClusteringReceptionist

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaClusteringSharding extends BetelgeuseAkkaClustering {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaClusteringReceptionist])


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with clustering-sharding.conf with fallback to...")
    ConfigFactory.parseResources("clustering-sharding.conf").withFallback(super.customizeConfiguration)
  }


  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BetelgeuseAkkaClusteringShardingExtension)
    LOGGER.info("Initializing done.")
  }


  def clusteringShardingExtension:BetelgeuseAkkaClusteringShardingExtension = BetelgeuseAkkaClusteringShardingExtension.get(system)




}
