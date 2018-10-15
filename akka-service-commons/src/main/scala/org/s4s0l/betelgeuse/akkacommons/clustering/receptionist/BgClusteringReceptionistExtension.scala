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

package org.s4s0l.betelgeuse.akkacommons.clustering.receptionist

import akka.actor.{Actor, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}
import akka.cluster.Cluster
import akka.cluster.client.{ClusterReceptionist, ClusterReceptionistSettings}
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.dispatch.Dispatchers
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BgClusteringPubSubExtension
import org.s4s0l.betelgeuse.akkacommons.utils.PubSubWithDefaultMediator

/**
  * @author Marcin Wielgus
  */
class BgClusteringReceptionistExtension(private val system: ExtendedActorSystem) extends Extension {


  private val config = system.settings.config.getConfig("akka.cluster.client.receptionist")
  private val role: Option[String] = config.getString("role") match {
    case "" ⇒ None
    case r ⇒ Some(r)
  }

  /**
    * Returns true if this member is not tagged with the role configured for the
    * receptionist.
    */
  def isTerminated: Boolean = Cluster(system).isTerminated || !role.forall(Cluster(system).selfRoles.contains)

  /**
    * Register the actors that should be reachable for the clients in this [[DistributedPubSubMediator]].
    * TODO: make this mediator name parametrized
    */
  val pubSubMediator: ActorRef = BgClusteringPubSubExtension(system).mediator

  val asPubSubWithDefaultMediator: PubSubWithDefaultMediator = new PubSubWithDefaultMediator {
    override def getDefaultPubSubMediator: ActorRef = pubSubMediator
  }

  def registerByName(name: String, ref: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit =
    BgClusteringPubSubExtension(system).registerByName(name, ref, pubSubMediator)

  /**
    * Register an actor that should be reachable for the clients.
    * The clients can send messages to this actor with `Send` or `SendToAll` using
    * the path elements of the `ActorRef`, e.g. `"/user/myservice"`.
    */
  def register(actor: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit =
    BgClusteringPubSubExtension(system).register(actor, pubSubMediator)

  /**
    * A registered actor will be automatically unregistered when terminated,
    * but it can also be explicitly unregistered before termination.
    */
  def unregister(actor: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit =
    BgClusteringPubSubExtension(system).unregister(actor.path.toStringWithoutAddress, pubSubMediator)


  /**
    * Register an actor that should be reachable for the clients to a named topic.
    * Several actors can be registered to the same topic name, and all will receive
    * published messages.
    * The client can publish messages to this topic with `Publish`.
    */
  def subscribe(topic: String, actor: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit =
    BgClusteringPubSubExtension(system).subscribe(topic, actor, pubSubMediator)


  /**
    * A registered subscriber will be automatically unregistered when terminated,
    * but it can also be explicitly unregistered before termination.
    */
  def unsubscribe(topic: String, actor: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit =
    BgClusteringPubSubExtension(system).unsubscribe(topic, actor, pubSubMediator)


  /**
    * Publishes to pub sub
    */
  def publish(topicName: String, msg: Any): Unit = BgClusteringPubSubExtension(system).publish(topicName, msg, pubSubMediator)

  /**
    * sends to pub sub directly
    */
  def send(ref: String, msg: Any): Unit = BgClusteringPubSubExtension(system).send(ref, msg, pubSubMediator)

  /**
    * The [[ClusterReceptionist]] actor
    */
  private val receptionist: ActorRef = {
    if (isTerminated)
      system.deadLetters
    else {
      val name = config.getString("name")
      val dispatcher = config.getString("use-dispatcher") match {
        case "" ⇒ Dispatchers.DefaultDispatcherId
        case id ⇒ id
      }
      // important to use val mediator here to activate it outside of ClusterReceptionist constructor
      val mediator = pubSubMediator
      val ret = system.systemActorOf(ClusterReceptionist.props(mediator, ClusterReceptionistSettings(config))
        .withDispatcher(dispatcher), name)
      register(system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case message => sender() ! message
        }
      }), "bg-receptionist-echo"))
      ret
    }
  }

  /**
    * Returns the underlying receptionist actor, particularly so that its
    * events can be observed via subscribe/unsubscribe.
    */
  def underlying: ActorRef =
    receptionist


}


object BgClusteringReceptionistExtension extends ExtensionId[BgClusteringReceptionistExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): BgClusteringReceptionistExtension = system.extension(this)

  override def apply(system: ActorSystem): BgClusteringReceptionistExtension = system.extension(this)

  override def lookup(): BgClusteringReceptionistExtension.type = BgClusteringReceptionistExtension

  override def createExtension(system: ExtendedActorSystem): BgClusteringReceptionistExtension =
    new BgClusteringReceptionistExtension(system)

}