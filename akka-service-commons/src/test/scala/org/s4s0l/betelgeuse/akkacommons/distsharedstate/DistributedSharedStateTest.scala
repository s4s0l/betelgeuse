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

package org.s4s0l.betelgeuse.akkacommons.distsharedstate

import akka.actor.ActorRef
import akka.actor.Status.{Failure, Status, Success}
import akka.util.Timeout
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClient
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedState.NewVersionedValueListener.NewVersionResult
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedState.{CachedValueListeningConsumer, NewVersionedValueListener, VersionedCache}
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedStateTest.ListeningLogger
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateActor
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.{SetValue, SetValueOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.serialization.BgSerialization
import org.s4s0l.betelgeuse.akkacommons.test.{BgTestJackson, BgTestRoach}
import org.s4s0l.betelgeuse.akkacommons.{BgService, BgServiceId}

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
    with BgSerialization {
    override protected def systemName: String = "origin"

    override protected def portBase: Int = 1

    var origin: OriginStateActor.Protocol[String] = _


    override def customizeConfiguration: Config =
      super.customizeConfiguration
        .withFallback(clusteringClientCreateConfig(BgServiceId("satellite2", 3)))
        .withFallback(clusteringClientCreateConfig(BgServiceId("satellite1", 2)))

    override protected def initialize(): Unit = {
      super.initialize()
      val dist = DistributedSharedState.createStateDistributionToRemoteServices[String]("state",
        List(BgServiceId("satellite1", 2), BgServiceId("satellite2", 3)))
      origin = OriginStateActor.startSharded(OriginStateActor.Settings("state", dist, 4 seconds))
    }
  })

  private val satellite1 = testWith(new BgService with BgPersistenceJournalRoach with BgClusteringReceptionist with BgClusteringSharding with BgClusteringClient {
    override protected def systemName: String = "satellite1"

    override protected def portBase: Int = 2

    var consumer: CachedValueListeningConsumer[String, ListeningLogger] = _

    override protected def initialize(): Unit = {
      super.initialize()
      val dist = DistributedSharedState.createSatelliteStateDistribution[String]("state", _ => Future.successful(List()))
      consumer = dist.createCachedValueListeningConsumer[String, ListeningLogger]("listenerOne", it => Future(s"enriched:$it"), 10 minutes)(new ListeningLogger(_))
      dist.enable()
    }

  })
  private val satellite2 = testWith(new BgService with BgPersistenceJournalRoach with BgClusteringReceptionist with BgClusteringSharding with BgClusteringClient {
    override protected def systemName: String = "satellite2"

    override protected def portBase: Int = 3

    var consumer: CachedValueListeningConsumer[String, ListeningLogger] = _

    override protected def onInitialized(): Unit = {
      super.onInitialized()
      val dist = DistributedSharedState.createSatelliteStateDistribution[String]("state", _ => Future.successful(List()))
      consumer = dist.createCachedValueListeningConsumer[String, ListeningLogger]("listenerOne", it => Future(s"enriched:$it"), 10 minutes)(new ListeningLogger(_))
      dist.enable()
    }
  })


  feature("Setting up a distributed shared state is doable in < 6 lines of code") {
    scenario("Origin value change is propagated to all parties and can be accessed at satellite side") {


      satellite1.service.consumer.consumer.nextSuccess()
      satellite2.service.consumer.consumer.nextSuccess()
      val value = SetValue("1", "valueOne")
      origin.service.origin.setValueMsg(value)(origin.execContext, origin.self)
      origin.testKit.expectMsg(SetValueOk(value.messageId, VersionedId("1", 1)))
      assert(satellite1.service.consumer.consumer.getPromisedValue(4 second) == (VersionedId("1", 1), "enriched:valueOne"))
      assert(Await.result(satellite1.service.consumer.cache.getVersion("1")(satellite1.execContext, satellite1.self), 4 second) == VersionedId("1", 1))
      assert(Await.result(satellite1.service.consumer.cache.getVersion("2")(satellite1.execContext, satellite1.self), 4 second) == VersionedId("2", 0))
      assert(Await.result(satellite1.service.consumer.cache.getValue(VersionedId("1", 1))(satellite1.execContext, satellite1.self, 1 second), 1 second) == "enriched:valueOne")
      assertThrows[Exception](Await.result(satellite1.service.consumer.cache.getValue(VersionedId("2", 1))(satellite1.execContext, satellite1.self, 1 second), 1 second))
      assert(satellite2.service.consumer.consumer.getPromisedValue(2 second) == (VersionedId("1", 1), "enriched:valueOne"))

    }

    scenario("Origin value change is propagated to all parties, but when listener fails it will be retried") {
      Given("One of the listeners fails")
      satellite2.service.consumer.consumer.nextSuccess()
      satellite1.service.consumer.consumer.nextFail()
      When("new version is published")
      val value = SetValue("1", "valueTwo")
      origin.service.origin.setValueMsg(value)(origin.execContext, origin.self)
      Then("We get confirmation from origin")
      origin.testKit.expectMsg(SetValueOk(value.messageId, VersionedId("1", 2)))
      And("All listeners were called")
      assert(satellite1.service.consumer.consumer.getPromisedValue(4 second) == (VersionedId("1", 2), "enriched:valueTwo"))
      assert(satellite2.service.consumer.consumer.getPromisedValue(1 second) == (VersionedId("1", 2), "enriched:valueTwo"))
      When("Next listener callback will be success")
      satellite1.service.consumer.consumer.nextSuccess()
      satellite2.service.consumer.consumer.nextSuccess()
      //we wait till redelivery occurs
      Thread.sleep(3000)
      Then("We expect one more call")
      assert(satellite1.service.consumer.consumer.getPromisedValue(4 second) == (VersionedId("1", 2), "enriched:valueTwo"))
      assert(satellite2.service.consumer.consumer.getPromisedValue(1 second) == (VersionedId("1", 2), "enriched:valueTwo"))
      satellite1.service.consumer.consumer.nextSuccess()
      satellite2.service.consumer.consumer.nextSuccess()
      And("After that no more calls")
      assertThrows[Exception](satellite1.service.consumer.consumer.getPromisedValue(5 second))


    }
  }

}


object DistributedSharedStateTest {

  class ListeningLogger(val cache: VersionedCache[String]) extends NewVersionedValueListener[String] {
    var receivedValues: List[(VersionedId, String)] = List()
    @volatile
    var receivedPromise: Promise[(VersionedId, String)] = Promise()

    private var next: Future[Status] = Future.successful(Success(1))


    def getPromisedValue(duration: FiniteDuration): (VersionedId, String) = Await.result(receivedPromise.future, duration)

    override def onNewVersionAsk(versionedId: VersionedId, richValue: String)
                                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[NewVersionResult] = {
      synchronized {
        println("Got!!")
        receivedValues = (versionedId, richValue) :: receivedValues
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
      }
    }

    def nextSuccess(): Unit = {
      synchronized {
        next = Future.successful(Success(1))
        receivedPromise = Promise()
      }
    }

  }

}








