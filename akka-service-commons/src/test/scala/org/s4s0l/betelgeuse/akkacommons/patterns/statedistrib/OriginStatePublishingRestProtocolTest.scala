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

/*
 * Copyright© 2017 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.persistence.AtLeastOnceDelivery
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject.{DomainObjectSettings, Id}
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObjectTest.SomeValue
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.StateDistributorProtocol
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStatePublishingActor.{Protocol, Settings, ValidationException}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.{GetValueVersion, ValueVersionResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.{VersionedEntityActor, VersionedEntityRestProtocol, VersionedId}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.JournalReader
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.serialization.BgSerializationJackson
import org.s4s0l.betelgeuse.akkacommons.test.{BgTestJackson, BgTestRoach}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps
import scala.util.Success

/**
  * @author Marcin Wielgus
  */
class OriginStatePublishingRestProtocolTest extends
  BgTestRoach with ScalatestRouteTest with BgTestJackson {


  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.second)

  implicit val self: ActorRef = ActorRef.noSender

  private val aService = testWith(new BgPersistenceJournalRoach
    with BgClusteringSharding with BgSerializationJackson {
  })


  feature("Explicitly publishing actor can be reached via rest api") {
    scenario("Publication is not allowed when validation fails") {
      implicit val toM: ToEntityMarshaller[SomeValue] = aService.service.httpMarshalling.marshaller[SomeValue]
      implicit val fM: FromEntityUnmarshaller[Id] = aService.service.httpMarshalling.unmarshaller[Id]

      Given("Origin state publishing Actor that always fails validation")
      val distributor = stub[StateDistributorProtocol[SomeValue]]
      val versionedEntity: OriginStatePublishingActor.Protocol[SomeValue] = {
        val ref = aService.service.clusteringShardingExtension.start("test2", Props(new OriginStatePublishingActor(Settings("test2", distributor)) {
          override protected def validatePublication(versionedId: VersionedId): Future[Option[ValidationException]] = Future.successful(Some(new ValidationException("No!")))
        }), VersionedEntityActor.entityExtractor orElse OriginStatePublishingActor.entityExtractor)
        Protocol(ref, "test2")
      }
      val route = createRoute("test2", versionedEntity)


      When("We create some value")
      val id: String = Post("/v1/objects/test2", SomeValue("value1")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should fullyMatch regex """\{"id":"[a-z0-9\-]+"\}"""
        responseAs[Id].id
      }


      Then("Publish is not possible")
      Put(s"/v1/objects/test2/$id/actions/publish/invoke?version=1") ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldBe """{"error":"No!"}"""
      }

      Then("We see no publication in publication statuses action")
      Get(s"/v1/objects/test2/$id/actions/publication-status/invoke") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe s"""{"statuses":[]}"""
      }


    }

    scenario("Publication state is restored") {
      implicit val toM: ToEntityMarshaller[SomeValue] = aService.service.httpMarshalling.marshaller[SomeValue]
      implicit val fM: FromEntityUnmarshaller[Id] = aService.service.httpMarshalling.unmarshaller[Id]

      Given("Origin state publishing Actor that always works")
      val distributor = stub[StateDistributorProtocol[SomeValue]]
      val ref = aService.service.clusteringShardingExtension.start("test3", Props(new OriginStatePublishingActor(Settings("test3", distributor))), VersionedEntityActor.entityExtractor orElse OriginStatePublishingActor.entityExtractor)
      val versionedEntity: OriginStatePublishingActor.Protocol[SomeValue] = Protocol(ref, "test3")
      val route = createRoute("test3", versionedEntity)


      When("We create some value")
      val id: String = Post("/v1/objects/test3", SomeValue("value1")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should fullyMatch regex """\{"id":"[a-z0-9\-]+"\}"""
        responseAs[Id].id
      }
      And("We publish change")
      Put(s"/v1/objects/test3/$id/actions/publish/invoke?version=1") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe """"""
      }
      And("We poison kill actor")
      ref.tell(GetValueVersion(id, "123"), aService.self)
      aService.testKit.expectMsg(ValueVersionResult("123", VersionedId(id, 1)))
      aService.testKit.lastSender ! PoisonPill


      And("If we ask for publication status")
      Get(s"/v1/objects/test3/$id/actions/publication-status/invoke") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe s"""{"statuses":[{"versionedId":{"id":"$id","version":1},"completed":false}]}"""
      }

    }

    scenario("Publication is sent only on explicit request") {
      implicit val toM: ToEntityMarshaller[SomeValue] = aService.service.httpMarshalling.marshaller[SomeValue]
      implicit val fM: FromEntityUnmarshaller[Id] = aService.service.httpMarshalling.unmarshaller[Id]

      Given("Origin state publishing Actor")
      val distributor = new StateDistributorProtocol[SomeValue] {
        var publishCount: Int = 0
        val beforeReplyPromise: Promise[Boolean] = Promise[Boolean]()
        val afterReplyPromise: Promise[Boolean] = Promise[Boolean]()

        override def stateChanged(msg: StateDistributorProtocol.OriginStateChanged[SomeValue])(implicit sender: ActorRef): Unit = {}

        override def deliverStateChange(from: AtLeastOnceDelivery)(versionedId: VersionedId, value: SomeValue, expectedConfirmIn: FiniteDuration): Unit = {
          publishCount = publishCount + 1
          beforeReplyPromise.complete(Success(true))
          afterReplyPromise.future.onComplete(_ =>
            from.self ! OriginStateDistributor.StateDistributorProtocol.OriginStateChangedOk(1L, versionedId)
          )

        }
      }
      val versionedEntity = OriginStatePublishingActor.startSharded[SomeValue](Settings("test1", distributor))(aService.service.clusteringShardingExtension)
      val route = createRoute("test1", versionedEntity)


      When("We create some value")
      val id: String = Post("/v1/objects/test1", SomeValue("value1")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should fullyMatch regex """\{"id":"[a-z0-9\-]+"\}"""
        responseAs[Id].id
      }

      And("we update it then")
      Put(s"/v1/objects/test1/$id", SomeValue("value2")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe """"""
      }

      When("We publish without version")
      Put(s"/v1/objects/test1/$id/actions/publish/invoke") ~> route ~> check {
        Then("We expect it to fail")
        responseAs[String] shouldBe """{"error":"Missing action parameters: version"}"""
        status shouldEqual StatusCodes.BadRequest
      }

      When("We publish with wrong version")
      Put(s"/v1/objects/test1/$id/actions/publish/invoke?version=notANumber") ~> route ~> check {
        Then("We expect it to fail")
        responseAs[String] shouldBe """{"error":"Invalid version param."}"""
        status shouldEqual StatusCodes.BadRequest
      }

      When("We publish with wrong id")
      Put(s"/v1/objects/test1/$id-wrong/actions/publish/invoke?version=12") ~> route ~> check {
        Then("We expect it to fail")
        responseAs[String] shouldBe """{"error":"Not Found"}"""
        status shouldEqual StatusCodes.NotFound
      }


      And("We publish change")
      Put(s"/v1/objects/test1/$id/actions/publish/invoke?version=2") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe """"""
      }

      Await.ready(distributor.beforeReplyPromise.future, 10.seconds)

      Then("We see one publication in publication statuses action")
      Get(s"/v1/objects/test1/$id/actions/publication-status/invoke") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe s"""{"statuses":[{"versionedId":{"id":"$id","version":2},"completed":false}]}"""
      }

      And("Publication request was called only once by an actor")
      assert(distributor.publishCount == 1)

      distributor.afterReplyPromise.complete(Success(true))

      Then("We see one publication in publication statuses action marked as completed!")
      Thread.sleep(1000)
      Get(s"/v1/objects/test1/$id/actions/publication-status/invoke") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe s"""{"statuses":[{"versionedId":{"id":"$id","version":2},"completed":true}]}"""
      }

      Then("We get 404 for missing entity when getting publication status for missing resource")
      Get(s"/v1/objects/test1/$id-missing/actions/publication-status/invoke") ~> route ~> check {
        Then("We expect it to fail")
        responseAs[String] shouldBe """{"error":"Not Found"}"""
        status shouldEqual StatusCodes.NotFound
      }

    }
  }

  private def createRoute(name: String, versionedEntity: OriginStatePublishingActor.Protocol[SomeValue]): Route = {

    val restProtocol: RestDomainObject.RestProtocol = new OriginStatePublishingRestProtocol[SomeValue, String] with VersionedEntityRestProtocol[SomeValue, String] {

      override def originStatePublishingActorProtocol: OriginStatePublishingActor.Protocol[SomeValue] = versionedEntity

      override protected def domainObjectSettings: DomainObjectSettings[String, SomeValue, String] = new DomainObjectSettings()

      override protected def domainObjectType: String = name

      override def version: String = "v1"

      override protected def versionedEntityActorProtocol: VersionedEntityActor.Protocol[SomeValue] = versionedEntity

      override protected def journalRead: JournalReader = aService.service.journalReader

    }

    restProtocol.createRoute(aService.execContext, aService.self, aService.service.httpMarshalling)
  }


}


