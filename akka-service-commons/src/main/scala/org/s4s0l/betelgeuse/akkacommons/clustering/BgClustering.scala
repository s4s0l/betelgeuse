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

package org.s4s0l.betelgeuse.akkacommons.clustering

import akka.cluster.Cluster
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.remoting.BgRemoting


trait BgClustering extends BgRemoting {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgClustering])

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with clustering.conf with fallback to...")
    ConfigFactory.parseResources("clustering.conf").withFallback(super.customizeConfiguration)
  }


  abstract override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    LOGGER.debug("Seed nodes : " + config.getStringList("akka.cluster.seed-nodes"))
    val lock = registerOnInitializedBlocker()
    cluster registerOnMemberUp {
      onClusterUp()
      unregisterOnInitializedBlocker(lock)
    }
    LOGGER.info("Initializing done.")
  }

  /**
    * called when current node is up in cluster
    */
  protected def onClusterUp(): Unit = {
    LOGGER.info("Node is up in cluster.")
  }

  protected final def leaveCluster(): Unit = {
    LOGGER.info("Leaving cluster.")
    cluster.leave(cluster.selfAddress)
  }

  final def cluster: Cluster = {
    Cluster(system)
  }


}
