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

package org.s4s0l.betelgeuse.akkaauth.audit

import java.util.{Date, UUID}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkaauth.BgAuthProviderTest._
import org.s4s0l.betelgeuse.akkaauth._
import org.s4s0l.betelgeuse.akkaauth.audit.StreamingAuditDto._
import org.s4s0l.betelgeuse.akkaauth.common.UserId
import org.s4s0l.betelgeuse.akkaauth.manager.AdditionalUserAttrsManager
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.concurrent.ScalaFutures
import scalikejdbc._

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

/**
  * @author Marcin Wielgus
  */
class BgAuthAuditCollectorTest extends BgTestRoach
  with ScalatestRouteTest
  with ScalaFutures {

  val defaultTimeout: FiniteDuration = 15.seconds

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(defaultTimeout)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(defaultTimeout, 300.millis)

  private val aService = testWith(
    new BgPersistenceJournalRoach
      with BgAuthRemoteProvider[String]
      with BgAuthProviderStreamingAudit[String]
      with BgAuthClientStreamingAudit[String]
      with BgAuthHttpProvider[String]
      with BgAuthAuditCollector {


      override def customizeConfiguration: Config = {
        ConfigFactory.parseString(s"bg.auth.streaming.topic = ${UUID.randomUUID().toString}")
          .withFallback(super.customizeConfiguration)
      }

      override protected def jwtAttributeMapper: AdditionalUserAttrsManager[String] =
        SampleJwtAttributes
    })

  private def route = Route.seal(aService.service.httpRoute)

  private val csrf = addHeader("X-XSRF-TOKEN", "123") ~>
    addHeader(Cookie("XSRF-TOKEN" -> "123"))

  def ensureAdminExists(): UserId = {
    implicit val self: ActorRef = ActorRef.noSender
    implicit val timeout: Timeout = defaultTimeout
    val adminCreationProcess =
      aService.service.bgAuthPasswordManager.verifyLogin(adminPasswordCredentials.login)
        .flatMap {
          case Some(userId) => Future.successful(userId)
          case None =>
            aService.service.bgAuthManager
              .createUser(adminDetailedAttributes, Some(adminPasswordCredentials))
        }
    whenReady(adminCreationProcess)(identity)
  }

  feature("Auth events can be streamed through kafka to db") {
    scenario("Smoke full test") {
      val userId = ensureAdminExists()

      Post("/auth/login", adminPasswordCredentials) ~> csrf ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
      Post("/auth/login", adminPasswordCredentials.copy(login = "wrong")) ~> csrf ~> route ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Get("/auth/verify") ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      val results = AllUtils.tryNTimes("waiting for results in db", 10) {
        aService.service.dbAccess.query { implicit session =>
          val res = sql"select event from auth_audit_log"
            .map(rs => aService.service
              .serializationJackson
              .simpleFromString[StreamingAuditDto](rs.string(1)))
            .list()
            .apply()
          assert(res.size == 3)
          res
        }
      }

      val testableProviderEvents = results
        .filter(_.isInstanceOf[AuthProviderEventDto])
        .sortBy(_.timestamp)
        .map(_.asInstanceOf[AuthProviderEventDto])
        .map(it => it.copy(
          id = "_",
          authInfo = it.authInfo.map(_.copy(tokenId = "_")),
          timestamp = new Date(0)))
      val serviceInfo = ServiceInfo("BgAuthAuditCollectorTest", "1")
      val routeInfo = RouteInfo("unknown", "POST", "http://example.com/auth/login", "/auth/login")
      val authInfo = Some(AuthInfoDto("_", "access", Some("admin"), userId.id, Map("something" -> "has a value")))
      assert(testableProviderEvents == List(
        AuthProviderEventDto("_", serviceInfo, routeInfo, "login", authInfo, None, None, None, new Date(0)),
        AuthProviderEventDto("_", serviceInfo, routeInfo, "providerError", None, None, None, Some("No credentials found for login wrong"), new Date(0))
      ))

      val testableClientEvents = results
        .filter(_.isInstanceOf[AuthClientEventDto])
        .sortBy(_.timestamp)
        .map(_.asInstanceOf[AuthClientEventDto])
        .map(it => it.copy(
          id = "_",
          authInfo = it.authInfo.map(_.copy(tokenId = "_")),
          timestamp = new Date(0)))
      val routeInfoVerify = RouteInfo("unknown", "GET", "http://example.com/auth/verify", "/auth/verify")
      assert(testableClientEvents == List(
        AuthClientEventDto("_", serviceInfo, routeInfoVerify, "tokenMissing", None, List(), None, new Date(0)),
      ))
    }
  }
}
