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



package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.{Actor, ActorRef}
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BgClusteringPubSubExtension.NamedPut
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BgClusteringPubSubProvider

import scala.concurrent.Future

/**
  *
  * TODO: subscribe and register should return futures!!!
  * TODO: should be splitted to PubSubBroker and PubSubSender or sth
  * TODO: should use ActorTargets for sending
  *
  * @author Marcin Wielgus
  */
trait PubSubWithDefaultMediator extends BgClusteringPubSubProvider {

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
