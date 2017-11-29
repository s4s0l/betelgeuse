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

package org.s4s0l.betelgeuse.akkacommons.clustering.sharding

import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.{Config, ConfigObject}

/**
  * @author Marcin Wielgus
  */
class BgClusteringShardingExtension(private val system: ExtendedActorSystem) extends Extension {

  private lazy val configShardingDefaults: Config = system.settings.config.getConfig("akka.cluster.sharding")
  private lazy val configShards: ConfigObject = system.settings.config.getObject("akka.cluster.shards")

  final def start(typeName: String,
                  entityProps: Props,
                  extractEntityId: ShardRegion.ExtractEntityId,
                  numberOfShards: Option[Int] = None)
  : ActorRef = {
    val config = clusterShardingConfig(typeName)
    val desiredShards = numberOfShards.getOrElse(config.getInt("number-of-shards"))
    ClusterSharding(system).start(
      typeName = typeName,
      entityProps = entityProps,
      settings = ClusterShardingSettings(config),
      extractEntityId = extractEntityId,
      extractShardId = startEntityShardExtractor(desiredShards, extractEntityId))
  }

  final def lookup(typeName: String): ActorRef = {
    ClusterSharding(system).shardRegion(typeName)
  }

  private final def clusterShardingConfig(typeName: String): Config = {
    if (configShards.containsKey(typeName)) {
      configShards.get(typeName).asInstanceOf[ConfigObject].toConfig.withFallback(configShardingDefaults)
    } else {
      configShardingDefaults
    }
  }


  private def startEntityShardExtractor(numberOfShards: Int, idExtractor: ShardRegion.ExtractEntityId): ShardRegion.ExtractShardId = {
    case ShardRegion.StartEntity(id) =>
      (id.hashCode % numberOfShards).toString
    case a if idExtractor.isDefinedAt(a) =>
      (idExtractor.apply(a)._1.hashCode % numberOfShards).toString
  }


}


object BgClusteringShardingExtension extends ExtensionId[BgClusteringShardingExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): BgClusteringShardingExtension = system.extension(this)

  override def apply(system: ActorSystem): BgClusteringShardingExtension = system.extension(this)

  override def lookup(): BgClusteringShardingExtension.type = BgClusteringShardingExtension

  override def createExtension(system: ExtendedActorSystem): BgClusteringShardingExtension =
    new BgClusteringShardingExtension(system)

}
