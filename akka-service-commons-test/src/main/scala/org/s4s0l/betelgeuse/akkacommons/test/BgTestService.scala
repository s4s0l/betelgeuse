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
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.TestedService
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
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

  def initializeServices(): Unit = {
    servicesUnderTest.foreach(_.startService())
  }

  def validateServices(): Unit = {
    assert(servicesUnderTest.map(_.service.asInstanceOf[BgService].serviceId.portBase)
      .distinct.size == servicesUnderTest.size, "each service in test should have different portBase!")
    assert(servicesUnderTest.map(_.service.asInstanceOf[BgService].serviceId.systemName)
      .distinct.size == servicesUnderTest.size, "each service in test should have different systemName!")

  }

  def createServices(): Unit = {
    servicesUnderTest.foreach(_.createService())
  }

  override protected def afterAll(): Unit = {
    servicesUnderTest.foreach(_.stopService())
  }

}


object BgTestService {


  class WithService[T <: BgService](ts: TestedService[T]) {
    implicit val defaultTimeout: Timeout = ts.defaultTimeout
    implicit val service: T = ts.service
    implicit val system: ActorSystem = ts.system
    implicit val testKit: TestKit with ImplicitSender = ts.testKit
    implicit val actorMaterializer: ActorMaterializer = ts.actorMaterializer
    implicit val self: ActorRef = ts.self
    implicit val execContext: ExecutionContextExecutor = ts.execContext
  }

  class TestedService[T <: BgService](bgServiceFactory: => T) {

    val defaultTimeout: Timeout = Timeout(1 second)

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
