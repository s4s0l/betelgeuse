/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 13:42
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import akka.actor.{Actor, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.cluster.client.ClusterClient
import akka.pattern.ask
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.client.ClusterClientsSupervisor.{AllReferencesMessage, GetAllReferencesMessage}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaClusteringClientExtension(private val system: ExtendedActorSystem) extends Extension {

  private val clientActors: Map[String, ActorRef] = {
    implicit val timeout: Timeout = 45 seconds
    val asking = (system.actorOf(ClusterClientsSupervisor.props(), "cluster-client-supervisor") ? GetAllReferencesMessage).mapTo[AllReferencesMessage]
    Await.result(asking, 45 seconds).references
  }

  def client(name: String): ActorRef = clientActors(name)

  def asClientTarget(name:String): ClusterClientTarget = new ClusterClientTarget(client(name))

  def send(name: String, actorPath: String, msg: Any, localAffinity: Boolean = true)(implicit sender: ActorRef = Actor.noSender): Unit = {
    client(name) ! ClusterClient.Send(actorPath, msg, localAffinity)
  }

  def sendAll(name: String, actorPath: String, msg: Any, localAffinity: Boolean = true)(implicit sender: ActorRef = Actor.noSender): Unit = {
    client(name) ! ClusterClient.SendToAll(actorPath, msg)
  }

  def publish(name: String, topic: String, msg: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    client(name) ! ClusterClient.Publish(topic, msg)
  }

  def question(name: String, actorPath: String, msg: Any, localAffinity: Boolean = true)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] = {
    client(name) ? ClusterClient.Send(actorPath, msg, localAffinity)
  }

}

object BetelgeuseAkkaClusteringClientExtension extends ExtensionId[BetelgeuseAkkaClusteringClientExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): BetelgeuseAkkaClusteringClientExtension = system.extension(this)

  override def apply(system: ActorSystem): BetelgeuseAkkaClusteringClientExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaClusteringClientExtension.type = BetelgeuseAkkaClusteringClientExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaClusteringClientExtension =
    new BetelgeuseAkkaClusteringClientExtension(system)
}
