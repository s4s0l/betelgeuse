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

package org.s4s0l.betelgeuse.akkacommons.test

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.TestedService

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * This should be deprecated! in favour of [[BgTestService]]
  * for now it adapts apis to old mechanizm but it makes whole api
  * cumbersome
  *
  * @author Marcin Wielgus
  */
@deprecated("Please migrate to BgTestService", "?")
trait BgServiceSpecLike[T <: BgService]
  extends BgTestService {

  def createService(): T

  var service: T = _
  implicit var system: ActorSystem = _
  var testKit: TestKit with ImplicitSender = _
  var defaultTimeout = Timeout(10 seconds)

  implicit def self: ActorRef = testKit.testActor

  implicit def execContext: ExecutionContextExecutor = service.executor

  private var guard = false

  def restartService(): Unit = {
    super.restartServices()
  }

  override protected def beforeAll(): Unit = {
    guard = true
    testWith(createService())
    guard = false
    super.beforeAll()

  }

  override def testWith[X <: BgService](bgServiceFactory: => X): TestedService[X] = {
    if (!guard) {
      throw new Exception("BgServiceSpecLike can be used only for testing single service")
    }
    val ts = new TestedService[X](bgServiceFactory) {
      override def startService(): Unit = {
        super.startService()
        BgServiceSpecLike.this.service = service.asInstanceOf[T]
        BgServiceSpecLike.this.system = system
        BgServiceSpecLike.this.testKit = testKit
      }
    }
    servicesUnderTest = ts :: servicesUnderTest
    ts
  }


}
