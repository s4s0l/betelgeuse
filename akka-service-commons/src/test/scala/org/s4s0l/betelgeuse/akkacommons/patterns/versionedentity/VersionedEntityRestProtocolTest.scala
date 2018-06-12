/*
 * Copyright© 2018 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject.DomainObjectSettings
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObjectTest.SomeValue
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Settings
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.JournalReader
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach

import scala.concurrent.duration._

/**
  *
  * TODO: rewrite it so that VersionedEntityActor protocol is a mock, will be much faster
  *
  * @author Marcin Wielgus
  */
class VersionedEntityRestProtocolTest extends
  BgTestRoach with ScalatestRouteTest {


  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.second)

  implicit val self: ActorRef = ActorRef.noSender

  private val aService = testWith(new BgPersistenceJournalRoach
    with BgClusteringSharding {
    private implicit val toInt: String => Int = x => x.toInt
    private lazy val versionedEntity = VersionedEntityActor.startSharded[SomeValue](Settings("rest-proto-sample"))
    lazy val restProtocol: VersionedEntityRestProtocol[SomeValue, Int] = createProtocol("rest-proto-sample", 1, versionedEntity)

  })

  feature("Versioned Entity protocol can be mapped to rest domain object protocol") {
    scenario("All operations can be performed via rest api") {
      implicit val toM: ToEntityMarshaller[SomeValue] = aService.service.httpMarshalling.marshaller[SomeValue]
      implicit val fM: FromEntityUnmarshaller[List[String]] = aService.service.httpMarshalling.unmarshaller[List[String]]

      var id: String = ""
      val route: Route = aService.service.restProtocol.createRoute(aService.service.executor, aService.self, aService.service.httpMarshalling)
      Post("/1/objects/rest-proto-sample", SomeValue("value1")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should fullyMatch regex """\{"id":"[a-z0-9\-]+"\}"""
      }
      Get("/1/objects/rest-proto-sample") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should fullyMatch regex """\["[a-z0-9\-]+"\]"""
        id = responseAs[List[String]].head
      }

      Get(s"/1/objects/rest-proto-sample/$id") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe """{"value":"value1"}"""
      }
      Put(s"/1/objects/rest-proto-sample/$id", SomeValue("value2")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe """"""
      }
      Get(s"/1/objects/rest-proto-sample/$id") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe """{"value":"value2"}"""
      }

      Get(s"/1/objects/rest-proto-sample/missing-$id") ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[String] shouldBe """{"error":"Not Found"}"""
      }
    }


  }


  private def createProtocol(name: String, _version: Int, versionedEntity: VersionedEntityActor.Protocol[SomeValue]): VersionedEntityRestProtocol[SomeValue, Int] = {

    new VersionedEntityRestProtocol[SomeValue, Int] {

      override protected def domainObjectSettings: DomainObjectSettings[String, SomeValue, Int] = new DomainObjectSettings()

      override protected def domainObjectType: String = name

      override def version: Int = _version

      override protected def versionedEntityActorProtocol: VersionedEntityActor.Protocol[SomeValue] = versionedEntity

      override protected def journalRead: JournalReader = aService.service.journalReader

    }
  }

}
