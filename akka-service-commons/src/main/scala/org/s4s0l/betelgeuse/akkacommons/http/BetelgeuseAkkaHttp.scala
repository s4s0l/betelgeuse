/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

trait BetelgeuseAkkaHttp extends BetelgeuseAkkaService {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaHttp])
  private var bindingFuture: Future[ServerBinding] = _

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with http.conf with fallback to...")
    ConfigFactory.parseResources("http.conf").withFallback(super.customizeConfiguration)
  }


  def httpRoute: Route = {
    pathPrefix("version") {
      complete("There will be an version")
    }
  }

  abstract protected override def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    bindingFuture = Http().bindAndHandle(pathPrefix(systemName) {
      httpRoute
    }, config.getString("akka.http.bind-hostname"), config.getInt("akka.http.port"))
    import org.s4s0l.betelgeuse.utils.AllUtils._
    if (config.boolean("akka.http.async-start").getOrElse(false)) {
      Await.result(bindingFuture, 30 seconds)
    }else {
      bindingFuture.onComplete {
        case Success(_) =>
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
