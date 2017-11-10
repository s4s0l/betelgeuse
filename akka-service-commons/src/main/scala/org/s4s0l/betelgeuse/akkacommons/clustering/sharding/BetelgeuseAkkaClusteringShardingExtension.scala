/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-04 13:01
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.sharding

import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.{Config, ConfigObject}

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaClusteringShardingExtension(private val system: ExtendedActorSystem) extends Extension {

  private lazy val configShardingDefaults: Config = system.settings.config.getConfig("akka.cluster.sharding")
  private lazy val configShards: ConfigObject = system.settings.config.getObject("akka.cluster.shards")

  final def clusterShardingConfig(typeName: String): Config = {
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

}


object BetelgeuseAkkaClusteringShardingExtension extends ExtensionId[BetelgeuseAkkaClusteringShardingExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): BetelgeuseAkkaClusteringShardingExtension = system.extension(this)

  override def apply(system: ActorSystem): BetelgeuseAkkaClusteringShardingExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaClusteringShardingExtension.type = BetelgeuseAkkaClusteringShardingExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaClusteringShardingExtension =
    new BetelgeuseAkkaClusteringShardingExtension(system)

}
