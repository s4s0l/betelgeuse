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

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import akka.Hack
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Cancellable, Props}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.japi.Util.immutableSeq
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigObject}
import org.s4s0l.betelgeuse.akkacommons.BgServiceExtension
import org.s4s0l.betelgeuse.akkacommons.clustering.client.ClusterClientsSupervisor.{AllReferencesMessage, CheckDnsForServiceMessage, DnsForServiceMessage, GetAllReferencesMessage}
import org.s4s0l.betelgeuse.akkacommons.utils.DnsUtils

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
object ClusterClientsSupervisor {

  object GetAllReferencesMessage

  case class AllReferencesMessage(references: Map[String, ActorRef])

  case class CheckDnsForServiceMessage(serviceName: String)

  case class DnsForServiceMessage(serviceName: String, addresses: Seq[ActorPath])

  def props(): Props = {
    Props[ClusterClientsSupervisor].withDispatcher("cluster-client-supervisor-dispatcher")
  }

}

class ClusterClientsSupervisor extends Actor with ActorLogging {
  implicit val dispatcherContext: ExecutionContextExecutor = context.dispatcher
  val configDefaults: Config = context.system.settings.config.getConfig("akka.cluster.client")
  val configClients: ConfigObject = context.system.settings.config.getObject("akka.cluster.clients")

  val resolvedClientConfigs: Map[String, Config] = configClients
    .keySet().asScala
    .foldLeft(Map[String, Config]())((m, clientName) => {
      m + (clientName -> configClients.get(clientName).asInstanceOf[ConfigObject].toConfig.withFallback(configDefaults))
    })


  val clientActors: Map[String, ActorRef] = resolvedClientConfigs.map {
    case (clientName, cfg) =>
      val actorRef = context.actorOf(ClusterClient.props(ClusterClientSettings(cfg)), s"cluster-client-$clientName")
      clientName -> actorRef
  }

  val schedulers: Iterable[Cancellable] = resolvedClientConfigs
    .filter(_._2.getBoolean("resolve-dns") || BgServiceExtension.get(context.system).serviceInfo.docker)
    .map {
      case (name, config) =>
        context.system.scheduler.schedule(Duration.Zero, config.getDuration("resolve-dns-interval", MILLISECONDS).millis, self, CheckDnsForServiceMessage(name))
    }

  val publishedContacts: mutable.Map[String, Seq[ActorPath]] = mutable.Map[String, Seq[ActorPath]]()


  override def postStop(): Unit = {
    schedulers.foreach(_.cancel())
  }

  override def receive: Receive = {
    case GetAllReferencesMessage =>
      sender() ! AllReferencesMessage(clientActors)
    case CheckDnsForServiceMessage(clientName) =>
      log.debug("Will lookup dns for client {}", clientName)
      checkDnsNamesForClient(clientName)
    case DnsForServiceMessage(clientName, s) =>
      log.debug("Got dns for client {}, they are {}", clientName, s)
      setDnsNamesForClient(clientName, s)
  }

  def checkDnsNamesForClient(clientName: String): Unit = {
    val config = resolvedClientConfigs(clientName)
    val initialContacts = immutableSeq(config.getStringList("initial-contacts"))
      .map(it => it.replace("@dns.", "@"))
      .map(ActorPath.fromString)
    pipe(DnsUtils.lookupActorPaths(initialContacts).map(s => DnsForServiceMessage(clientName, s))).pipeTo(self)
  }

  def setDnsNamesForClient(clientName: String, addresses: Seq[ActorPath]): Unit = {
    val orderedAddresses = addresses.sortBy(_.address.host.get)
    //todo: publishedContacts should come from subscription on client! because
    // sending contacts may fail and then we got stuck with never updated
    // state
    if (publishedContacts.getOrElse(clientName, List()) != orderedAddresses) {
      log.info("For client {} dns resolutions changed to {}", clientName, orderedAddresses)
      publishedContacts(clientName) = orderedAddresses
      clientActors(clientName) ! Hack.contacts(orderedAddresses.map(_.toString).toVector)
    } else {
      log.debug("For client {} dns resolutions have not changed, list was {}", clientName, orderedAddresses)
    }
    //todo: wtf?? why do it again? why do it at all? It's obvoiusly a work arounf
    //for note from above....
    clientActors(clientName) ! Hack.contacts(orderedAddresses.map(_.toString).toVector)
  }

}