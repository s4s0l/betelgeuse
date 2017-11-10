/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-12 20:16
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import akka.Hack
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Cancellable, Props}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.japi.Util.immutableSeq
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigObject}
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaServiceExtension
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
    .filter(_._2.getBoolean("resolve-dns") || BetelgeuseAkkaServiceExtension.get(context.system).serviceInfo.docker)
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
    // sending contacts may fail and then wy got stuck with never updated
    // state
    if (publishedContacts.getOrElse(clientName, List()) != orderedAddresses) {
      log.info("For client {} dns resolutions changed to {}", clientName, orderedAddresses)
      publishedContacts(clientName) = orderedAddresses
      clientActors(clientName) ! Hack.contacts(orderedAddresses.map(_.toString).toVector)
    } else {
      log.debug("For client {} dns resolutions have not changed changed to {}", clientName, orderedAddresses)
    }

    clientActors(clientName) ! Hack.contacts(orderedAddresses.map(_.toString).toVector)
  }

}