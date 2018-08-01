/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

package org.s4s0l.betelgeuse.akkacommons.http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.BgVersionProvider.BgVersionsInfo

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

trait BgHttp extends BgService {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgHttp])
  private var bindingFuture: Future[ServerBinding] = _

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with http.conf with fallback to...")
    ConfigFactory.parseResources("http.conf").withFallback(super.customizeConfiguration)
  }


  def httpRoute: Route = {
    pathPrefix("version") {
      implicit val um: ToEntityMarshaller[BgVersionsInfo] = httpMarshalling.marshaller[BgVersionsInfo]
      complete(getVersionsInfo)
    }
  }

  abstract protected override def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    val bindAddress = config.getString("akka.http.bind-hostname")
    val bindPort = config.getInt("akka.http.port")
    bindingFuture = Http().bindAndHandle(pathPrefix(systemName) {
      httpRoute
    }, bindAddress, bindPort)
    import org.s4s0l.betelgeuse.utils.AllUtils._
    if (config.boolean("akka.http.async-start").getOrElse(false)) {
      Await.result(bindingFuture, 30 seconds)
    } else {
      bindingFuture.onComplete {
        case Success(_) =>
          LOGGER.info(s"Bound http to http://$bindAddress:$bindPort/$systemName")
        case Failure(ex) =>
          LOGGER.error("Unable to bind http!", ex)
          shutdown()
      }
    }

    LOGGER.info("Initializing done.")
  }

  abstract override protected def beforeShutdown(): Unit = {
    LOGGER.info("Http socket unbinding...")
    Await.result(bindingFuture.flatMap(a => a.unbind()), 30 seconds) // trigger unbinding from the port
    LOGGER.info("Http socket unbound.")
    super.beforeShutdown()
  }
}
