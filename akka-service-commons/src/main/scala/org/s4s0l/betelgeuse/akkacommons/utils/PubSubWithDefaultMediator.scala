/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 03:07
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.{Actor, ActorRef}
import akka.cluster.client.ClusterClient
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BetelgeuseAkkaClusteringPubSubExtension.NamedPut
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BetelgeuseAkkaClusteringPubSubProvider

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
trait PubSubWithDefaultMediator extends BetelgeuseAkkaClusteringPubSubProvider {

  def subscribe(topicName: String, ref: ActorRef, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! Subscribe(topicName, ref)

  def unsubscribe(topicName: String, ref: ActorRef, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! Unsubscribe(topicName, ref)

  def publish(topicName: String, msg: Any, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! Publish(topicName, msg)

  def subscribeInGroup(topicName: String, group: String, ref: ActorRef, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! new Subscribe(topicName, Some(group), ref)

  def unsubscribeInGroup(topicName: String, group: String, ref: ActorRef, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! new Unsubscribe(topicName, Some(group), ref)

  def publishGroup(topicName: String, msg: Any, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! new Publish(topicName, msg, sendOneMessageToEachGroup = true)

  def register(ref: ActorRef, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! Put(ref)

  def registerByName(name: String, ref: ActorRef, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! NamedPut(name, ref)

  def unregister(ref: String, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! Remove(ref)

  def send(ref: String, msg: Any, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit sender: ActorRef = Actor.noSender): Unit = mediatorActor ! Send(ref, msg, localAffinity = true)

  def question(ref: String, msg: Any, mediatorActor: ActorRef = getDefaultPubSubMediator)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] = {
    import akka.pattern._
    mediatorActor ? Send(ref, msg, localAffinity = true)
  }

}
