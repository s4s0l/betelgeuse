
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

package org.s4s0l.betelgeuse.akkacommons.test

import akka.actor.{ActorRef, ActorSystem}
import akka.serialization.Serialization
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.TestedService
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen, Matchers}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
trait BgTestService extends FeatureSpecLike
  with Matchers
  with BeforeAndAfterAll
  with GivenWhenThen
  with MockFactory {

  var servicesUnderTest: List[TestedService[_]] = List()

  def testWith[T <: BgService](bgServiceFactory: => T): TestedService[T] = {
    val ts = new TestedService[T](bgServiceFactory)
    servicesUnderTest = ts :: servicesUnderTest
    ts
  }

  def restartServices(): Unit = {

    servicesUnderTest.foreach(_.restartService())
  }

  override protected def beforeAll(): Unit = {
    createServices()
    validateServices()
    initializeServices()
  }

  var concurentRun = false

  def initializeServices(): Unit = {
    if (concurentRun) {
      import scala.concurrent.ExecutionContext.Implicits.global
      val eventualUnits: immutable.Seq[Future[Unit]] = servicesUnderTest.map(serv => Future(serv.startService()))
      val eventualUnit: Future[Seq[Unit]] = AllUtils.listOfFuturesToFutureOfList(eventualUnits)
      import concurrent.duration._
      Await.result(eventualUnit, 120 seconds)
    } else {
      servicesUnderTest.foreach(_.startService())
    }
  }

  def validateServices(): Unit = {
    assert(servicesUnderTest.map(_.service.asInstanceOf[BgService].serviceId.portBase)
      .distinct.lengthCompare(servicesUnderTest.size) == 0, "each service in test should have different portBase!")
    assert(servicesUnderTest.map(_.service.asInstanceOf[BgService].serviceId.systemName)
      .distinct.lengthCompare(servicesUnderTest.size) == 0, "each service in test should have different systemName!")

  }

  def createServices(): Unit = {
    if (concurentRun) {
      import scala.concurrent.ExecutionContext.Implicits.global
      val eventualUnits: immutable.Seq[Future[Unit]] = servicesUnderTest.map(serv => Future(serv.createService()))
      val eventualUnit: Future[Seq[Unit]] = AllUtils.listOfFuturesToFutureOfList(eventualUnits)
      import concurrent.duration._
      Await.result(eventualUnit, 60 seconds)
    } else {
      servicesUnderTest.foreach(_.createService())
    }
  }

  override protected def afterAll(): Unit = {
    if (concurentRun) {
      import scala.concurrent.ExecutionContext.Implicits.global
      val eventualUnits: immutable.Seq[Future[Unit]] = servicesUnderTest.map(serv => Future(serv.stopService()))
      val eventualUnit: Future[Seq[Unit]] = AllUtils.listOfFuturesToFutureOfList(eventualUnits)
      import concurrent.duration._
      Await.result(eventualUnit, 60 seconds)
    } else {
      servicesUnderTest.foreach(_.stopService())
    }
  }

}


object BgTestService {


  class WithService[T <: BgService](private[test] val testedService: TestedService[T]) {
    implicit val timeout: Timeout = testedService.timeout
    implicit val to: FiniteDuration = testedService.to
    implicit val service: T = testedService.service
    implicit val system: ActorSystem = testedService.system
    implicit val testKit: TestKit with ImplicitSender = testedService.testKit
    implicit val actorMaterializer: ActorMaterializer = testedService.actorMaterializer
    implicit val self: ActorRef = testedService.self
    implicit val execContext: ExecutionContextExecutor = testedService.execContext
    implicit val akkaSerialization: Serialization = testedService.service.serializer
    implicit val serializationJackson: JacksonJsonSerializer = testedService.service.serializationJackson
  }

  class TestedService[T <: BgService](bgServiceFactory: => T) {

    var to: FiniteDuration = 2 seconds
    var timeout: Timeout = Timeout(to)
    var service: T = _
    var system: ActorSystem = _
    var testKit: TestKit with ImplicitSender = _
    var actorMaterializer: ActorMaterializer = _

    def self: ActorRef = testKit.testActor

    def execContext: ExecutionContextExecutor = service.executor

    def restartService(): Unit = {
      stopService()
      createService()
      startService()
    }

    def createService(): Unit = {
      service = bgServiceFactory
    }

    def startService(): Unit = {
      system = service.run()
      testKit = new TestKit(system) with ImplicitSender
      actorMaterializer = ActorMaterializer()(system)
      assert(testKit != null)
    }

    def stopService(): Unit = {
      service.shutdown()
      service = _: T
      system = _: ActorSystem
      testKit = _: TestKit with ImplicitSender
      actorMaterializer = _: ActorMaterializer
    }
  }

}
