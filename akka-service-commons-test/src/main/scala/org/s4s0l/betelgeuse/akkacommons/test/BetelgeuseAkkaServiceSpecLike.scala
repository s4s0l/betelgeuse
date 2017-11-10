/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 21:19
 *
 */

package org.s4s0l.betelgeuse.akkacommons.test

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService
import org.scalamock.scalatest.MockFactory
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaServiceSpecLike[T <: BetelgeuseAkkaService] extends FeatureSpecLike
  with Matchers with BeforeAndAfterAll with GivenWhenThen
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

  def restartService():Unit = {
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
