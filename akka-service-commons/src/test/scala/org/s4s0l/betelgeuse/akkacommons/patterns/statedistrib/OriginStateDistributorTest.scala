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

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.{OriginStateChanged, OriginStateChangedNotOk, OriginStateChangedOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributorTest._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.s4s0l.betelgeuse.akkacommons.utils.QA.Uuid
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class OriginStateDistributorTest
  extends BgTestService {

  private val aService = testWith(new BgService {})

  val to: FiniteDuration = 5 second
  implicit val timeUnit: Timeout = to


  feature("Origin State distributor distributes to state to N other places and confirms it") {
    scenario("informs everybody around") {
      new WithService(aService) {

        Given("Two satellite states registered in state distributor")
        private val distributor = OriginStateDistributor.start(OriginStateDistributor.Settings("dist1", Map(
          "one" -> MockSatellite("one", successFuture, successFutureDistribution),
          "two" -> MockSatellite("two", successFuture, successFutureDistribution)
        )))

        When("Distribute change")
        distributor.stateChanged(OriginStateChanged(1, VersionedId("id1", 1), "value", to))(self)

        Then("Expect confirmation")
        testKit.expectMsg(to, OriginStateChangedOk(1))

        And("Change was delivered")
        private val emitted = queue.toArray(new Array[String](4))
        private val changes = List("one:SC:value:id1@1", "two:SC:value:id1@1")
        assert(changes.contains(emitted(0)))
        assert(changes.contains(emitted(1)))

        And("Change confirm was emitted")
        private val commits = List("one:SD:id1@1", "two:SD:id1@1")
        assert(commits.contains(emitted(2)))
        assert(commits.contains(emitted(3)))

      }
    }

    Seq(
      ("stateChangeNotConfirmedFailureFuture", failureFuture, "Failure returned by listener"),
      ("stateChangeNotConfirmExceptionFuture", exceptionFuture, "Exception from listener"),
      ("stateChangeNotConfirmTimeoutFuture", timeoutFuture, "Timeout by one of listeners"),
      ("stateChangeNotConfirmTakesTooLong", longFutureChange(1.1 second), "Takes too long")
    ).foreach { case (name, ff, display) =>
      scenario(s"Does not confirm if stateChange is not confirmed by one of satellites due to $display") {
        new WithService(aService) {

          Given("Two satellite states registered in state distributor")
          private val distributor = OriginStateDistributor.start(OriginStateDistributor.Settings(name, Map(
            "one" -> MockSatellite("one", ff, successFutureDistribution),
            "two" -> MockSatellite("two", successFuture, successFutureDistribution)
          )))

          When("Distribute change")
          distributor.stateChanged(OriginStateChanged(2, VersionedId("id1", 1), "value", 1 second))(self)

          Then("Expect No confirmation")
          assert(testKit.expectMsgClass(2 seconds, classOf[OriginStateChangedNotOk]).correlationId == 2)

          And("Change was delivered")
          private val emitted = queue.toArray(new Array[String](4))
          private val changes = List("one:SC:value:id1@1", "two:SC:value:id1@1")
          assert(changes.contains(emitted(0)))
          assert(changes.contains(emitted(1)))

          And("Change confirm was not emitted")
          assert(queue.size() == 2)
          queue.clear()
        }
      }
    }
    Seq(
      ("stateDistributionNotConfirmedFailureFuture", failureFutureDistribution, "Failure returned by listener"),
      ("stateDistributionNotConfirmExceptionFuture", exceptionFutureDistribution, "Exception from listener"),
      ("stateDistributionNotConfirmTimeoutFuture", timeoutFutureDistribution, "Timeout by one of listeners"),
      ("stateDistributionNotConfirmLongFuture", longFutureDistribution(1.1 second), "Takes too long")
    ).foreach { case (name, ff, display) =>
      scenario(s"Does not confirm if stateDistribution is not confirmed by one of satellites due to $display") {
        new WithService(aService) {
          Given("Two satellite states registered in state distributor")
          private val distributor = OriginStateDistributor.start(OriginStateDistributor.Settings(name, Map(
            "one" -> MockSatellite("one", successFuture, ff),
            "two" -> MockSatellite("two", successFuture, successFutureDistribution)
          )))

          When("Distribute change")
          distributor.stateChanged(OriginStateChanged(3, VersionedId("id1", 1), "value", 1 second))(self)

          Then("Expect No confirmation")
          assert(testKit.expectMsgClass(2 seconds, classOf[OriginStateChangedNotOk]).correlationId == 3)


          And("Change was delivered")
          private val emitted = queue.toArray(new Array[String](4))
          private val changes = List("one:SC:value:id1@1", "two:SC:value:id1@1")
          assert(changes.contains(emitted(0)))
          assert(changes.contains(emitted(1)))

          And("Change confirm was emitted")
          private val commits = List("one:SD:id1@1", "two:SD:id1@1")
          assert(commits.contains(emitted(2)))
          assert(commits.contains(emitted(3)))
          queue.clear()
        }
      }
    }


  }

  override def withFixture(test: NoArgTest): Outcome = {
    OriginStateDistributorTest.queue.clear()
    super.withFixture(test)
  }
}

object OriginStateDistributorTest {

  type FutureFactoryChange = (Uuid, ExecutionContext) => Future[StateChangeResult]
  type FutureFactoryDistribution = (Uuid, ExecutionContext) => Future[DistributionCompleteResult]
  val queue: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]
  val successFuture: FutureFactoryChange = (uuid: Uuid, executionContext: ExecutionContext) => Future(StateChangeOk(uuid))(executionContext)
  val failureFuture: FutureFactoryChange = (uuid: Uuid, executionContext: ExecutionContext) => Future(StateChangeNotOk(uuid, new Exception("?")))(executionContext)
  val exceptionFuture: FutureFactoryChange = (_: Uuid, _: ExecutionContext) => Future.failed(new Exception("?"))
  val timeoutFuture: FutureFactoryChange = (_: Uuid, _: ExecutionContext) => Future.never

  val successFutureDistribution: FutureFactoryDistribution = (uuid: Uuid, executionContext: ExecutionContext) => Future(DistributionCompleteOk(uuid))(executionContext)
  val failureFutureDistribution: FutureFactoryDistribution = (uuid: Uuid, executionContext: ExecutionContext) => Future(DistributionCompleteNotOk(uuid, new Exception("?")))(executionContext)
  val exceptionFutureDistribution: FutureFactoryDistribution = (_: Uuid, _: ExecutionContext) => Future.failed(new Exception("?"))
  val timeoutFutureDistribution: FutureFactoryDistribution = (_: Uuid, _: ExecutionContext) => Future.never

  def longFutureChange(finiteDuration: FiniteDuration): FutureFactoryChange = (uuid: Uuid, exec: ExecutionContext) => Future {
    Thread.sleep(finiteDuration.toMillis)
    StateChangeOk(uuid)
  }(exec)


  def longFutureDistribution(finiteDuration: FiniteDuration): FutureFactoryDistribution = (uuid: Uuid, exec: ExecutionContext) => Future {
    Thread.sleep(finiteDuration.toMillis)
    DistributionCompleteOk(uuid)
  }(exec)

  case class MockSatellite(name: String, stateChanged: FutureFactoryChange, stateDistributed: FutureFactoryDistribution) extends SatelliteProtocol[String] {
    /**
      * distributes state change
      */
    override def stateChange(msg: StateChange[String])
                            (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[StateChangeResult] = {
      queue.add(s"$name:SC:${msg.value}:${msg.versionedId}")
      stateChanged(msg.messageId, executionContext)
    }

    /**
      * informs that all destinations confirmed
      */
    override def distributionComplete(msg: DistributionComplete)
                                     (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[DistributionCompleteResult] = {
      queue.add(s"$name:SD:${msg.versionedId}")
      stateDistributed(msg.messageId, executionContext)
    }
  }


}
