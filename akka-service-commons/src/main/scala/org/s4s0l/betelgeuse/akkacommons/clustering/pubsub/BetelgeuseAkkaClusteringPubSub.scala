/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.pubsub

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BetelgeuseAkkaClustering
import org.s4s0l.betelgeuse.akkacommons.remoting.BetelgeuseAkkaRemoting


/**
  * alternatively as actor:
  * system.systemActorOf(DistributedPubSubMediator.props(settings).withDispatcher(dispatcher), name)
  */
trait BetelgeuseAkkaClusteringPubSub extends BetelgeuseAkkaClustering{

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaClustering])


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with pubsub.conf with fallback to...")
    ConfigFactory.parseResources("clustering-pubsub.conf").withFallback(super.customizeConfiguration)
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    val extension = system.registerExtension(BetelgeuseAkkaClusteringPubSubExtension)
    serviceExtension.pluginRegister(classOf[BetelgeuseAkkaClusteringPubSubProvider], () => extension)
    LOGGER.info("Initializing done.")
  }


  def clusteringPubSubExtension: BetelgeuseAkkaClusteringPubSubExtension =
    BetelgeuseAkkaClusteringPubSubExtension.get(system)

}


