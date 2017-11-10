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

package org.s4s0l.betelgeuse.akkacommons.clustering.pubsub

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BetelgeuseAkkaClustering


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


