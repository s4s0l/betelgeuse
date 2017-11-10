/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 11:55
 *
 */

package sandbox

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import org.s4s0l.betelgeuse.akkacommons._
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BetelgeuseAkkaClusteringReceptionist
import org.s4s0l.betelgeuse.akkacommons.http.stomp.BetelgeuseAkkaHttpStomp
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandler.StompHandlerSettings
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandlerApi._
import org.s4s0l.betelgeuse.akkacommons.serialization.BetelgeuseAkkaSerializationJackson

/**
  * @author Marcin Wielgus
  */
object WebSocketStomp extends BetelgeuseAkkaService
  with BetelgeuseAkkaSerializationJackson
  with BetelgeuseAkkaHttpStomp
  with BetelgeuseAkkaClusteringReceptionist {

  override def systemName: String = "web-socket-stomp"

  override protected def portBase = 66

  override def httpRoute: Route = {
    cors() {
      stomp(StompHandlerSettings("ws-testx", Some(clusteringReceptionistExtension.pubSubMediator)),
        _ => flow) ~ super.httpRoute
    }
  }

  val flow: Flow[StompClientMessage, Option[StompServerMessage], Any] = {
    Flow[StompClientMessage].collect {
      case StompClientSend(headers, source, id, payload) =>
        log.info(s"Got it: source=$source id=$id payload=$payload headers=$headers")
        clusteringReceptionistExtension.publish(source.topic("responses").toServerSpaceDestination, StompServerData(source.topic("responses"), "\"My payload!\""))
        clusteringReceptionistExtension.publish(source.user("responses").toServerSpaceDestination, StompServerData(source.user("responses"), "\"My payload!\""))
        Some(StompServerData(source.session("responses"), payload.get))
    }
  }

}