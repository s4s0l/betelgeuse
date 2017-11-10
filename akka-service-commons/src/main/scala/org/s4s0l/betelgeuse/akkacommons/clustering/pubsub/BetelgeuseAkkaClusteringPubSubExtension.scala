/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 13:14
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.pubsub

import akka.actor.{Actor, ActorRef, ActorSystem, Deploy, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSubSettings}
import akka.dispatch.Dispatchers
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BetelgeuseAkkaClusteringPubSubExtension.NamedPut
import org.s4s0l.betelgeuse.akkacommons.utils.PubSubWithDefaultMediator

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaClusteringPubSubExtension(private val system: ExtendedActorSystem) extends Extension with BetelgeuseAkkaClusteringPubSubProvider
  with PubSubWithDefaultMediator {

  private lazy val settings = DistributedPubSubSettings(system)

  private def isTerminated: Boolean =
    Cluster(system).isTerminated || !settings.role.forall(Cluster(system).selfRoles.contains)


  override def getDefaultPubSubMediator: ActorRef = mediator

  val asPubSubWithDefaultMediator: PubSubWithDefaultMediator = this

  val mediator: ActorRef = {
    if (isTerminated)
      system.deadLetters
    else {
      createMediator("defaultNamedPubSubMediator")
    }
  }

  def createMediator(mediatorActorName: String): ActorRef = {
    if (isTerminated) {
      throw new RuntimeException("unable to create mediator as system is closed!")
    }
    //TODO: handle custom mediator settings
    val dispatcher = system.settings.config.getString("akka.cluster.pub-sub.use-dispatcher") match {
      case "" ⇒ Dispatchers.DefaultDispatcherId
      case id ⇒ id
    }
    val props = Props(new DistributedPubSubMediator(settings) {
      private def handleNamedPut: Actor.Receive = {
        case NamedPut(name, ref) =>
          if (ref.path.address.hasGlobalScope)
            log.warning("Registered actor must be local: [{}]", ref)
          else {
            put(name, Some(ref))
            context.watch(ref)
          }
      }

      override def receive: PartialFunction[Any, Unit] = handleNamedPut.orElse(super.receive)
    }).withDeploy(Deploy.local)
    system.actorOf(props.withDispatcher(dispatcher), mediatorActorName)
  }


}


object BetelgeuseAkkaClusteringPubSubExtension extends ExtensionId[BetelgeuseAkkaClusteringPubSubExtension] with ExtensionIdProvider {

  @SerialVersionUID(1L) final case class NamedPut(name: String, ref: ActorRef)

  override def get(system: ActorSystem): BetelgeuseAkkaClusteringPubSubExtension = system.extension(this)

  override def apply(system: ActorSystem): BetelgeuseAkkaClusteringPubSubExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaClusteringPubSubExtension.type = BetelgeuseAkkaClusteringPubSubExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaClusteringPubSubExtension =
    new BetelgeuseAkkaClusteringPubSubExtension(system)

}
