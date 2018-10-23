/*
 * Copyright© 2018 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.clustering.sharding

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BgClustering
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist

/**
  * @author Marcin Wielgus
  */
trait BgClusteringSharding extends BgClustering {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgClusteringReceptionist])


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with clustering-sharding.conf with fallback to...")
    ConfigFactory.parseResources("clustering-sharding.conf").withFallback(super.customizeConfiguration)
  }


  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BgClusteringShardingExtension)
    LOGGER.info("Initializing done.")
  }


  implicit def clusteringShardingExtension: BgClusteringShardingExtension = BgClusteringShardingExtension.get(system)




}
