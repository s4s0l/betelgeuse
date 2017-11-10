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