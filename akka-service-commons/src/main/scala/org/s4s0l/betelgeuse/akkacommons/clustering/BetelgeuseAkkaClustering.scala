/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering

import akka.cluster.Cluster
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.remoting.BetelgeuseAkkaRemoting


trait BetelgeuseAkkaClustering extends BetelgeuseAkkaRemoting {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaClustering])

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
