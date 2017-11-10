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

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import akka.actor.{Actor, ActorRef}
import akka.cluster.client.ClusterClient
import akka.pattern.AskableActorRef
import akka.util.Timeout

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
class ClusterClientTarget(client: ActorRef) {

  def send(actorPath: String, msg: Any, localAffinity: Boolean = true)(implicit sender: ActorRef = Actor.noSender): Unit = {
    client ! ClusterClient.Send(actorPath, msg, localAffinity)
  }

  def sendAll(actorPath: String, msg: Any, localAffinity: Boolean = true)(implicit sender: ActorRef = Actor.noSender): Unit = {
    client ! ClusterClient.SendToAll(actorPath, msg)
  }

  def publish(topic: String, msg: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    client ! ClusterClient.Publish(topic, msg)
  }

  def question(actorPath: String, msg: Any, localAffinity: Boolean = true)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] = {
    new AskableActorRef(client) ? ClusterClient.Send(actorPath, msg, localAffinity)
  }

}
