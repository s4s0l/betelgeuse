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
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService
import org.scalamock.scalatest.MockFactory
import org.scalatest._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaServiceSpecLike[T <: BetelgeuseAkkaService]
  extends FeatureSpecLike
    with Matchers
    with BeforeAndAfterAll
    with GivenWhenThen
    with MockFactory {

  def createService(): T

  var service: T = _
  implicit var system: ActorSystem = _
  var testKit: TestKit with ImplicitSender = _
  val defaultTimeout = Timeout(10 seconds)

  implicit def self: ActorRef = testKit.testActor

  implicit def execContext: ExecutionContextExecutor = service.executor

  def initializeService(): Unit = {
    system = service.run()
    testKit = new TestKit(system) with ImplicitSender
  }

  def restartService(): Unit = {
    service.shutdown()
    service = createService()
    system = service.run()
    testKit = new TestKit(system) with ImplicitSender
    assert(testKit != null)
  }

  override protected def beforeAll(): Unit = {
    service = createService()
    initializeService()
    assert(testKit != null)
  }

  override protected def afterAll(): Unit = {
    service.shutdown()
  }

}
