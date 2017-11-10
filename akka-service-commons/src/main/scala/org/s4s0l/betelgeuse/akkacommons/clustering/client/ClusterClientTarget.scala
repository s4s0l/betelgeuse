/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 13:42
 *
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
