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

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.Status.{Failure, Status, Success}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.{OriginStateChanged, OriginStateChangedConfirm}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributorTest._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.test.BgServiceSpecLike
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class OriginStateDistributorTest
  extends BgServiceSpecLike[BgService] {

  override def createService(): BgService = new BgService {}

  val to: FiniteDuration = 5 second
  implicit val timeUnit: Timeout = to


  feature("Origin State distributor distributes to state to N other places and confirms it") {
    scenario("informs everybody around") {

      Given("Two satellite states registered in state distributor")
      val distributor = OriginStateDistributor.start(OriginStateDistributor.Settings("dist1", Map(
        "one" -> MockSatellite("one", successFuture, successFuture),
        "two" -> MockSatellite("two", successFuture, successFuture)
      )))

      When("Distribute change")
      distributor.stateChanged(OriginStateChanged(VersionedId("id1", 1), "value", to))(self)

      Then("Expect confirmation")
      testKit.expectMsg(to, OriginStateChangedConfirm(VersionedId("id1", 1)))

      And("Change was delivered")
      val emited = queue.toArray(new Array[String](4))
      val changes = List("one:SC:value:id1@1", "two:SC:value:id1@1")
      assert(changes.contains(emited(0)))
      assert(changes.contains(emited(1)))

      And("Change confirm was emitted")
      val commits = List("one:SD:id1@1", "two:SD:id1@1")
      assert(commits.contains(emited(2)))
      assert(commits.contains(emited(3)))

    }

    Seq(
      ("stateChangeNotConfirmedFailureFuture", failureFuture, "Failure returned by listener"),
      ("stateChangeNotConfirmExceptionFuture", exceptionFuture, "Exception from listener"),
      ("stateChangeNotConfirmTimeoutFuture", timeoutFuture, "Timeout by one of listeners"),
      ("stateChangeNotConfirmTakesTooLong", longFuture(1.1 second), "Takes too long"),
    ).foreach { case (name, ff, display) =>
      scenario(s"Does not confirm if stateChange is not confirmed by one of satellites due to $display") {


        Given("Two satellite states registered in state distributor")
        val distributor = OriginStateDistributor.start(OriginStateDistributor.Settings(name, Map(
          "one" -> MockSatellite("one", ff, successFuture),
          "two" -> MockSatellite("two", successFuture, successFuture)
        )))

        When("Distribute change")
        distributor.stateChanged(OriginStateChanged(VersionedId("id1", 1), "value", 1 second))(self)

        Then("Expect No confirmation")
        testKit.expectNoMsg(2 seconds)

        And("Change was delivered")
        val emitted = queue.toArray(new Array[String](4))
        val changes = List("one:SC:value:id1@1", "two:SC:value:id1@1")
        assert(changes.contains(emitted(0)))
        assert(changes.contains(emitted(1)))

        And("Change confirm was not emitted")
        assert(queue.size() == 2)
        queue.clear()
      }

    }
    Seq(
      ("stateDistributionNotConfirmedFailureFuture", failureFuture, "Failure returned by listener"),
      ("stateDistributionNotConfirmExceptionFuture", exceptionFuture, "Exception from listener"),
      ("stateDistributionNotConfirmTimeoutFuture", timeoutFuture, "Timeout by one of listeners"),
      ("stateDistributionNotConfirmLongFuture", longFuture(1.1 second), "Takes too long"),
    ).foreach { case (name, ff, display) =>
      scenario(s"Does not confirm if stateDistribution is not confirmed by one of satellites due to $display") {

        Given("Two satellite states registered in state distributor")
        val distributor = OriginStateDistributor.start(OriginStateDistributor.Settings(name, Map(
          "one" -> MockSatellite("one", successFuture, ff),
          "two" -> MockSatellite("two", successFuture, successFuture)
        )))

        When("Distribute change")
        distributor.stateChanged(OriginStateChanged(VersionedId("id1", 1), "value", 1 second))(self)

        Then("Expect No confirmation")
        testKit.expectNoMsg(2 second)

        And("Change was delivered")
        val emitted = queue.toArray(new Array[String](4))
        val changes = List("one:SC:value:id1@1", "two:SC:value:id1@1")
        assert(changes.contains(emitted(0)))
        assert(changes.contains(emitted(1)))

        And("Change confirm was emitted")
        val commits = List("one:SD:id1@1", "two:SD:id1@1")
        assert(commits.contains(emitted(2)))
        assert(commits.contains(emitted(3)))
        queue.clear()
      }
    }


  }

  override def withFixture(test: NoArgTest): Outcome = {
    OriginStateDistributorTest.queue.clear()
    super.withFixture(test)
  }
}

object OriginStateDistributorTest {

  type FutureFactory = (ExecutionContext) => Future[Status]
  val queue: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]
  val successFuture: FutureFactory = (executionContext: ExecutionContext) => Future(Success(0))(executionContext)
  val failureFuture: FutureFactory = (executionContext: ExecutionContext) => Future(Failure(new Exception("?")))(executionContext)
  val exceptionFuture: FutureFactory = (_: ExecutionContext) => Future.failed(new Exception("?"))
  val timeoutFuture: FutureFactory = (_: ExecutionContext) => Future.never

  def longFuture(finiteDuration: FiniteDuration): FutureFactory = (exec: ExecutionContext) => Future {
    Thread.sleep(finiteDuration.toMillis)
    Success(0)
  }(exec)


  case class MockSatellite(name: String, stateChanged: FutureFactory, stateDistributed: FutureFactory) extends SatelliteProtocol[String] {
    /**
      * distributes state change
      */
    override def stateChanged(versionedId: VersionedId, value: String, destination: String)
                             (implicit timeouted: Timeout, executionContext: ExecutionContext)
    : Future[Status] = {
      assert(destination == name)
      queue.add(s"$name:SC:$value:$versionedId")
      stateChanged(executionContext)
    }

    /**
      * informs that all destinations confirmed
      */
    override def stateDistributed(versionedId: VersionedId, destination: String)
                                 (implicit timeouted: Timeout, executionContext: ExecutionContext)
    : Future[Status] = {
      assert(destination == name)
      queue.add(s"$name:SD:$versionedId")
      stateDistributed(executionContext)
    }
  }


}
