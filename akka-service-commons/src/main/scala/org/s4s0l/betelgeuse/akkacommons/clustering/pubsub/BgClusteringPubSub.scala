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

package org.s4s0l.betelgeuse.akkacommons.clustering.pubsub

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.clustering.BgClustering


/**
  * alternatively as actor:
  * system.systemActorOf(DistributedPubSubMediator.props(settings).withDispatcher(dispatcher), name)
  */
trait BgClusteringPubSub extends BgClustering {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgClustering])


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with pubsub.conf with fallback to...")
    ConfigFactory.parseResources("clustering-pubsub.conf").withFallback(super.customizeConfiguration)
  }

  implicit def clusteringPubSubExtension: BgClusteringPubSubExtension =
    BgClusteringPubSubExtension.get(system)

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    val extension = system.registerExtension(BgClusteringPubSubExtension)
    serviceExtension.pluginRegister(classOf[BgClusteringPubSubProvider], () => extension)
    LOGGER.info("Initializing done.")
  }

}


