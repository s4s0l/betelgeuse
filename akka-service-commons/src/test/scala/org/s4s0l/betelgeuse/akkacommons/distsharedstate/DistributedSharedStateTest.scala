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

package org.s4s0l.betelgeuse.akkacommons.distsharedstate

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorRef
import akka.actor.Status.{Failure, Status, Success}
import akka.util.Timeout
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClient
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedState.VersionedCache
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedStateTest._
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.NewVersionedValueListener.{NewVersionOk, NewVersionResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateActor.Protocol
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateActor.Protocol.{GetPublicationStatus, GetPublicationStatusOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.ValidationError
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteValueHandler.HandlerResult
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.{OriginStateActor, SatelliteValueHandler}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.{SetValue, SetValueOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.test.{BgTestJackson, BgTestRoach}
import org.s4s0l.betelgeuse.akkacommons.{BgService, BgServiceId}
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class DistributedSharedStateTest extends BgTestRoach with BgTestJackson {

  concurentRun = true

  private val origin = testWith(new BgService
    with BgPersistenceJournalRoach
    with BgClusteringReceptionist
    with BgClusteringSharding
    with BgClusteringClient
    with BgOriginStateService {
    override protected def systemName: String = "origin"

    override protected def portBase: Int = 1

    var origin: OriginStateActor.Protocol[String] = _

    override def customizeConfiguration: Config =
      super.customizeConfiguration
        .withFallback(clusteringClientCreateConfig(BgServiceId("satellite2", 3)))
        .withFallback(clusteringClientCreateConfig(BgServiceId("satellite1", 2)))

    override protected def initialize(): Unit = {
      super.initialize()
      val dist = createRemoteDistribution[String](
        "state", List(BgServiceId("satellite1", 2), BgServiceId("satellite2", 3)))
      origin = createOriginState("state", dist, stateDistributionRetryInterval = 4.seconds)
    }
  })

  private val satellite1 = testWith(new BgService
    with BgPersistenceJournalRoach
    with BgClusteringReceptionist
    with BgClusteringSharding
    with BgClusteringClient
    with BgSatelliteStateService {

    override protected def systemName: String = "satellite1"

    override protected def portBase: Int = 2

    var consumer: ListeningLogger = _
    var cache: VersionedCache[String] = _
    var startupNotifier: DistributedSharedState.ListenerStartupNotifier = _
    var globalNotifier: DistributedSharedState.ListenerStartupNotifier = _
    val lastGlobalListenerCall: AtomicReference[List[String]] = new AtomicReference[List[String]](List())

    override protected def initialize(): Unit = {
      super.initialize()
      val dist = createSatelliteStateFactory[String, String]("state", new SatelliteValueHandler[String, String] {
        override def handle(versionedId: VersionedId, input: String)(implicit executionContext: ExecutionContext)
        : Future[HandlerResult[String]] =
          handler1.handle(versionedId, input)
      })
      cache = dist.createCache("listenerOne", it => Future(s"enriched:$it"), 10.minutes)
      consumer = new ListeningLogger()
      globalNotifier = dist.addGlobalListener("xxx", new NewVersionedValueListener[String] {
        override def onNewVersionAsk(versionedId: VersionedId, aValue: String)
                                    (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
        : Future[NewVersionResult] = {
          lastGlobalListenerCall.accumulateAndGet(List(aValue), (t: List[String], u: List[String]) => {
            t ++ u
          })
          Future.successful(NewVersionOk(versionedId))
        }
      })
      startupNotifier = cache.addListener(consumer)
      dist.enable()
    }

  })

  private val satellite2 = testWith(new BgService
    with BgPersistenceJournalRoach
    with BgClusteringReceptionist
    with BgClusteringSharding
    with BgClusteringClient
    with BgSatelliteStateService {
    override protected def systemName: String = "satellite2"

    override protected def portBase: Int = 3

    var consumer: ListeningLogger = _
    var cache: VersionedCache[String] = _
    var startupNotifier: DistributedSharedState.ListenerStartupNotifier = _

    override protected def initialize(): Unit = {
      super.initialize()
      val dist = createSatelliteStateFactory[String, String]("state", new SatelliteValueHandler[String, String] {
        override def handle(versionedId: VersionedId, input: String)(implicit executionContext: ExecutionContext)
        : Future[HandlerResult[String]] =
          handler2.handle(versionedId, input)
      })
      cache = dist.createCache("listenerOne", it => Future(s"enriched:$it"), 10.minutes)
      consumer = new ListeningLogger()
      startupNotifier = cache.addListener(consumer)
      dist.enable()
    }
  })


  feature("Setting up a distributed shared state is doable in < 6 lines of code") {
    scenario("Origin value change is propagated to all parties and can be accessed at satellite side") {

      satellite1.service.consumer.nextSuccess()
      satellite2.service.consumer.nextSuccess()
      val value = SetValue("1", "valueOne")
      origin.service.origin.setValueMsg(value)(origin.execContext, origin.self)
      origin.testKit.expectMsg(SetValueOk(value.messageId, VersionedId("1", 1)))
      assert(satellite1.service.consumer.getPromisedValue(4 second) == (VersionedId("1", 1), "enriched:VALUEONE"))
      assert(Await.result(satellite1.service.cache
        .getVersion("1")(satellite1.execContext, satellite1.self), 4 second) == VersionedId("1", 1))
      assert(Await.result(satellite1.service.cache
        .getVersion("2")(satellite1.execContext, satellite1.self), 4 second) == VersionedId("2", 0))
      assert(Await.result(satellite1.service.cache
        .getValue(VersionedId("1", 1))(satellite1.execContext, satellite1.self, 1 second), 1 second) == "enriched:VALUEONE")
      assertThrows[Exception](Await.result(satellite1.service
        .cache.getValue(VersionedId("2", 1))(satellite1.execContext, satellite1.self, 1 second), 1 second))
      assert(satellite2.service.consumer.getPromisedValue(2 second) == (VersionedId("1", 1), "enriched:valueone"))
    }

    scenario("Cache Listeners and global listeners are notified on satellite") {
      val id = VersionedId("xxxx", 1)

      Given("Satellites accept values")
      satellite1.service.consumer.nextSuccess()
      satellite2.service.consumer.nextSuccess()
      When("We set a value")
      val value = SetValue("xxxx", "valueXxx")
      origin.service.origin.setValueMsg(value)(origin.execContext, origin.self)
      origin.testKit.expectMsg(4 seconds ,SetValueOk(value.messageId, id))

      Then("Listeners are notified")
      assert(satellite1.service.consumer.getPromisedValue(4 second) == (id, "enriched:VALUEXXX"))
      assert(satellite2.service.consumer.getPromisedValue(4 second) == (id, "enriched:valuexxx"))

      When("We startup notify")
      satellite1.service.consumer.nextSuccess()
      satellite2.service.consumer.nextSuccess()
      satellite1.service.lastGlobalListenerCall.set(List())

      Await.result(satellite1.service.globalNotifier.notifyStartupValues(satellite1.service.executor, 14 second, ActorRef.noSender), 10 seconds)
      Await.result(satellite1.service.startupNotifier.notifyStartupValues(satellite1.service.executor, 14 second, ActorRef.noSender), 10 seconds)
      Await.result(satellite2.service.startupNotifier.notifyStartupValues(satellite2.service.executor, 14 second, ActorRef.noSender), 10 seconds)

      Then("Listeners are notified")
      assert(satellite1.service.consumer.totalCalls.get.contains("enriched:VALUEXXX"))
      assert(satellite2.service.consumer.totalCalls.get.contains("enriched:valuexxx"))
      assert(satellite1.service.lastGlobalListenerCall.get.contains("VALUEXXX"))

      val latestDistributedVersion1 = satellite1.service.cache.getDistributedVersion(id.id)(satellite1.execContext, satellite1.self)
      assert(Await.result(latestDistributedVersion1, satellite1.to).version == 1)
      val latestDistributedVersion2 = satellite2.service.cache.getDistributedVersion(id.id)(satellite2.execContext, satellite2.self)
      assert(Await.result(latestDistributedVersion2, satellite2.to).version == 1)

    }

    scenario("Origin value change is propagated to all parties, but when listener fails it will be retried") {
      Given("One of the listeners fails")
      satellite2.service.consumer.nextSuccess()
      satellite1.service.consumer.nextFail()
      When("new version is published")
      val value = SetValue("2", "valueTwo")
      origin.service.origin.setValueMsg(value)(origin.execContext, origin.self)
      Then("We get confirmation from origin")
      origin.testKit.expectMsg(SetValueOk(value.messageId, VersionedId("2", 1)))
      And("All listeners were called")
      assert(satellite1.service.consumer.getPromisedValue(4 second) == (VersionedId("2", 1), "enriched:VALUETWO"))
      assert(satellite2.service.consumer.getPromisedValue(1 second) == (VersionedId("2", 1), "enriched:valuetwo"))
      When("Next listener callback will be success")
      satellite1.service.consumer.nextSuccess()
      satellite2.service.consumer.nextSuccess()
      //we wait till redelivery occurs
      Thread.sleep(3000)
      Then("We expect one more call")
      assert(satellite1.service.consumer.getPromisedValue(4 second) == (VersionedId("2", 1), "enriched:VALUETWO"))
      assert(satellite2.service.consumer.getPromisedValue(1 second) == (VersionedId("2", 1), "enriched:valuetwo"))
      satellite1.service.consumer.nextSuccess()
      satellite2.service.consumer.nextSuccess()
      And("After that no more calls")
      assertThrows[Exception](satellite1.service.consumer.getPromisedValue(5 second))
    }

    scenario("Origin value change is propagated to all parties, but when somebody  fails validation it will not be retried") {
      Given("One of the listeners fails")
      satellite2.service.consumer.nextSuccess()
      satellite1.service.consumer.nextSuccess()
      handler1 = (i: String) => {
        handler1CalledPromise.complete(util.Success(i))
        Right(ValidationError(Seq("fuck")))
      }
      When("new version is published")
      val value = SetValue("3", "valueThree")
      origin.service.origin.setValueMsg(value)(origin.execContext, origin.self)
      Then("We get confirmation from origin")
      origin.testKit.expectMsg(SetValueOk(value.messageId, VersionedId("3", 1)))
      And("All handlers were called")
      assert(getHandler1PromisedValue(4 second) == "valueThree")
      assert(getHandler2PromisedValue(4 second) == "valueThree")
      resetHandlers()

      When("we wait till redelivery occurs")
      Thread.sleep(3000)
      Then("After that no more calls")
      assertThrows[Exception](getHandler1PromisedValue(5 second))
      assertThrows[Exception](getHandler1PromisedValue(1 second))

      And("We see validation result in statuses")
      val sts: Future[Protocol.GetPublicationStatusResult] = origin.service.origin.publishStatus(GetPublicationStatus("3"))(origin.execContext, origin.self)
      val res: Protocol.GetPublicationStatusResult = Await.result(sts, origin.to)
      assert(res.asInstanceOf[GetPublicationStatusOk].value.statuses.size == 1)
      assert(res.asInstanceOf[GetPublicationStatusOk].value.statuses.head.completed)
      assert(res.asInstanceOf[GetPublicationStatusOk].value.statuses.head.validationError.validationErrors.head == "fuck")
    }
  }

  override def withFixture(test: NoArgTest): Outcome = {
    resetHandlers()

    super.withFixture(test)
  }
}


object DistributedSharedStateTest {

  var handler1CalledPromise: Promise[String] = Promise[String]()
  var handler2CalledPromise: Promise[String] = Promise[String]()
  var handler1: SatelliteValueHandler[String, String] = _
  var handler2: SatelliteValueHandler[String, String] = _

  private def resetHandlers(): Unit = {
    handler1CalledPromise = Promise[String]()
    handler2CalledPromise = Promise[String]()
    handler1 = (i: String) => {
      handler1CalledPromise.complete(util.Success(i))
      Left(Some(i.toUpperCase()))
    }
    handler2 = (i: String) => {
      handler2CalledPromise.complete(util.Success(i))
      Left(Some(i.toLowerCase()))
    }
  }

  resetHandlers()

  def getHandler1PromisedValue(duration: FiniteDuration): String = Await.result(handler1CalledPromise.future, duration)

  def getHandler2PromisedValue(duration: FiniteDuration): String = Await.result(handler2CalledPromise.future, duration)

  class ListeningLogger() extends NewVersionedValueListener[String] {
    var receivedValues: List[(VersionedId, String)] = List()
    @volatile
    var receivedPromise: Promise[(VersionedId, String)] = Promise()
    val totalCalls: AtomicReference[List[String]] = new AtomicReference[List[String]](List())
    private var next: Future[Status] = Future.successful(Success(1))

    def getPromisedValue(duration: FiniteDuration): (VersionedId, String) = Await.result(receivedPromise.future, duration)

    override def onNewVersionAsk(versionedId: VersionedId, richValue: String)
                                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[NewVersionResult] = {
      synchronized {
        receivedValues = (versionedId, richValue) :: receivedValues
        totalCalls.accumulateAndGet(List(richValue), (t: List[String], u: List[String]) => {
          t ++ u
        })
        receivedPromise.complete(util.Success((versionedId, richValue)))
        next.map {
          case Success(_) => NewVersionedValueListener.NewVersionOk(versionedId)
          case Failure(ex) => NewVersionedValueListener.NewVersionNotOk(versionedId, ex)
        }
      }
    }

    def nextFail(): Unit = {
      synchronized {
        next = Future.successful(Failure(new Exception("ex!")))
        receivedPromise = Promise()
      }
    }

    def nextFailFuture(): Unit = {

      synchronized {
        next = Future.failed(new Exception("ex!"))
        receivedPromise = Promise()
        totalCalls.set(List())
      }
    }

    def nextSuccess(): Unit = {
      synchronized {
        next = Future.successful(Success(1))
        receivedPromise = Promise()
        totalCalls.set(List())
      }
    }
  }

}








