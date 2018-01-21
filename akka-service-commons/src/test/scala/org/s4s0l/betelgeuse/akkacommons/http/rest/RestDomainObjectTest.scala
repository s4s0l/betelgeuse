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

package org.s4s0l.betelgeuse.akkacommons.http.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.headers.{RawHeader, `Content-Type`}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject._
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObjectTest.{AlwaysOk, SomeValue}
import org.s4s0l.betelgeuse.akkacommons.serialization.{HttpMarshalling, JacksonJsonSerializer}
import org.s4s0l.betelgeuse.akkacommons.utils.QA
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class RestDomainObjectTest extends FeatureSpec with ScalatestRouteTest with Matchers with MockFactory with GivenWhenThen {
  implicit val marshalling: HttpMarshalling = new HttpMarshalling(new JacksonJsonSerializer())
  implicit val toInt: String => Int = x => x.toInt
  implicit val sender: ActorRef = ActorRef.noSender
  //  implicit val ec: ExecutionContext = ExecutionContext.global
  feature("Actor protocols should be able to self create http routes for domain object actors") {
    scenario("When protocol is successful responses are delivered to the client") {

      val route = new AlwaysOk(1, "always-ok", new DomainObjectSettings()).createRoute
      implicit val toM: ToEntityMarshaller[SomeValue] = marshalling.marshaller[SomeValue]

      Get("/1/objects/always-ok") ~> route ~> check {
        responseAs[String] shouldEqual """["ok"]"""
        header("correlationId") shouldBe defined
        header[`Content-Type`] shouldBe Some(`Content-Type`(MediaTypes.`application/json`))
      }
      Get("/1/objects/always-ok/6") ~> route ~> check {
        header("correlationId") shouldBe defined
        responseAs[String] shouldEqual """{"value":"get:6"}"""
        header[`Content-Type`] shouldBe Some(`Content-Type`(MediaTypes.`application/json`))
      }
      Post("/1/objects/always-ok", SomeValue("x")) ~> route ~> check {
        header[`Content-Type`] shouldBe Some(`Content-Type`(MediaTypes.`application/json`))
        responseAs[String] should startWith("""{"id":"create:""")
        responseAs[String] should endWith(""":x"}""")
        header("correlationId") shouldBe defined
      }

      Put("/1/objects/always-ok/8", SomeValue("x")) ~> route ~> check {
        responseAs[String] shouldEqual """"""
        header("correlationId") shouldBe defined
      }

      Delete("/1/objects/always-ok/8") ~> route ~> check {
        responseAs[String] shouldEqual """"""
        header("correlationId") shouldBe defined
      }

      Get("/1/objects/always-ok/8/actions") ~> route ~> check {
        responseAs[String] shouldEqual """{"actions":[{"name":"query-action-1","method":"GET","params":["param1","param2"]},{"name":"query-action-2","method":"GET","params":["xxx"]},{"name":"idempotent-action","method":"PUT","params":[]},{"name":"non-idempotent-action","method":"POST","params":[]}]}"""
        header("correlationId") shouldNot be(defined)
      }

      Get("/1/objects/always-ok/8/actions/query-action-1") ~> route ~> check {
        responseAs[String] shouldEqual """{"name":"query-action-1","method":"GET","params":["param1","param2"]}"""
        header("correlationId") shouldNot be(defined)
      }

      info("should accept query params as action parameters")
      Get("/1/objects/always-ok/8/actions/query-action-1/invoke?param1=aaa") ~> route ~> check {
        responseAs[String] shouldEqual """"qa1:8:aaa""""
        header("correlationId") shouldBe defined
      }


      info("should accept headers as action parameters")
      Get("/1/objects/always-ok/9/actions/query-action-2/invoke").withHeaders(RawHeader("xxx", "value")) ~> route ~> check {
        responseAs[String] shouldEqual """"qa2:9:value""""
        header("correlationId") shouldBe defined
      }

      Put("/1/objects/always-ok/9/actions/idempotent-action/invoke") ~> route ~> check {
        responseAs[String] shouldEqual """"ia:9""""
        header("correlationId") shouldBe defined
      }

      Post("/1/objects/always-ok/91/actions/non-idempotent-action/invoke") ~> route ~> check {
        responseAs[String] shouldEqual """"na:91""""
        header("correlationId") shouldBe defined
      }

      val messageId = "sampleMessageId"
      Post("/1/objects/always-ok/91/actions/non-idempotent-action/invoke").withHeaders(RawHeader("messageId", messageId)) ~> route ~> check {
        header("correlationId") shouldBe Some(RawHeader("correlationId", messageId))
      }

    }
  }

  //TODO test NotOk responses

  //TODO test exceptions

}

object RestDomainObjectTest {

  class AlwaysOk(val version: Int, val domainObjectType: String, val domainObjectSettings: DomainObjectSettings[String, SomeValue, Int])
    extends RestDomainObject.DomainObjectProtocol[String, SomeValue, Int]
      with Gets[String, SomeValue, Int]
      with Updates[String, SomeValue, Int]
      with Creates[String, SomeValue, Int]
      with Deletes[String, SomeValue, Int]
      with Actions[String, SomeValue, Int] {

    override def get(msg: Get[String, Int])
                    (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[SomeValue]] =
      Future.successful(RestCommandOk(SomeValue(s"get:${msg.id}"), msg.messageId))

    override def list(msg: GetList[Int])
                     (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[List[String]]] =
      Future.successful(RestCommandOk(List("ok"), msg.messageId))

    override def update(msg: Update[String, SomeValue, Int])
                       (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"update:${msg.id}", msg.messageId))

    override def generateId: String = QA.uuid

    override def create(msg: Create[String, SomeValue, Int])
                       (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"create:${msg.id}:${msg.value.value}", msg.messageId))

    override def delete(msg: Delete[String, Int])
                       (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"delete:${msg.id}", msg.messageId))

    override def actions: Map[ActionDesc, ActionType] =
      super.actions ++ Map(
        Query("query-action-1", Set("param1", "param2")) -> ((a: Action[String, Int], c: RestProtocolContext) => queryAction1(a)(c.executionContext, c.sender)),
        Query("query-action-2", Set("xxx")) -> ((a: Action[String, Int], c: RestProtocolContext) => queryAction2(a)(c.executionContext, c.sender)),
        Idempotent("idempotent-action") -> ((a: Action[String, Int], c: RestProtocolContext) => idempotentAction(a)(c.executionContext, c.sender)),
        NonIdempotent("non-idempotent-action") -> ((a: Action[String, Int], c: RestProtocolContext) => nonIdempotentAction(a)(c.executionContext, c.sender))
      )


    def queryAction1(msg: Action[String, Int])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"qa1:${msg.id}:${msg.params("param1")}", msg.messageId))

    def queryAction2(msg: Action[String, Int])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"qa2:${msg.id}:${msg.params("xxx")}", msg.messageId))

    def idempotentAction(msg: Action[String, Int])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"ia:${msg.id}", msg.messageId))

    def nonIdempotentAction(msg: Action[String, Int])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[String]] =
      Future.successful(RestCommandOk(s"na:${msg.id}", msg.messageId))
  }

  case class SomeValue(value: String)

}
