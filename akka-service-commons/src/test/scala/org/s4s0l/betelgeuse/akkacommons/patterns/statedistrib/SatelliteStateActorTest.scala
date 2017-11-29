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

import akka.actor.Status.{Failure, Status, Success}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BetelgeuseAkkaClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.{SatelliteStateListener, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BetelgeuseAkkaPersistenceJournalCrate
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaTestWithCrateDb
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class SatelliteStateActorTest extends
  BetelgeuseAkkaTestWithCrateDb[BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding] {

  val to: FiniteDuration = 5 second
  implicit val timeUnit: Timeout = to

  override def createService(): BetelgeuseAkkaPersistenceJournalCrate
    with BetelgeuseAkkaClusteringSharding
  = new BetelgeuseAkkaPersistenceJournalCrate
    with BetelgeuseAkkaClusteringSharding {

  }

  feature("Satellite State actor is an VersionedEntityActor with ability to confirm distribution") {

    scenario("Does not confirm distribution if value was not introduced before") {

      Given("A new shard storing string values named testX")
      val protocol = SatelliteStateActor.startSharded[String](Settings("testX", successListener))(service.clusteringShardingExtension)

      When("stateDistributed is performed without prior stateChanged about entity id1 version 1")
      val changeDistributted = protocol.stateDistributed(VersionedId("id1", 1), "destination2")

      Then("Distribution confirmation is received and is a failure")
      assert(Await.result(changeDistributted, to).isInstanceOf[Failure])

      And("Notifier is not called")
      assert(listenerResponse.isEmpty)


      When("state changing entity id1 to version 1 and value 'valueone'")
      val changeStatus = protocol.stateChanged(VersionedId("id1", 1), "valueone", "destination1")

      Then("Version returned should have value == 0")
      assert(Await.result(changeStatus, to).isInstanceOf[Success])

      When("Confirm Distribution is send")
      val changeDistributted1 = protocol.stateDistributed(VersionedId("id1", 1), "destination2")

      Then("Distribution confirmation is received")
      assert(Await.result(changeDistributted1, to).isInstanceOf[Success])

      And("Notifier is completed")
      assert(listenerResponse.contains("valueone@id1@1"))
      listenerResponse = None

      When("stateDistributed is performed without prior stateChanged about entity id1 version 2")
      val changeDistributted2 = protocol.stateDistributed(VersionedId("id1", 2), "destination2")

      Then("Distribution confirmation is received and is a failure")
      assert(Await.result(changeDistributted2, to).isInstanceOf[Failure])


      And("Notifier is not called")
      assert(listenerResponse.isEmpty)


    }

    scenario("Does not confirm distribution if listener failed") {

      Given("A new shard storing string values named test3 with always failing listener")
      val protocol = SatelliteStateActor.startSharded[String](Settings("test3", failingListener))(service.clusteringShardingExtension)

      When("state changing entity id1 to version 1 and value 'valueone'")
      val change1Status = protocol.stateChanged(VersionedId("id1", 1), "valueone", "destination1")

      Then("Version returned should have value == 0")
      assert(Await.result(change1Status, to).isInstanceOf[Success])

      When("Confirm Distribution is send")
      val changeDistributted = protocol.stateDistributed(VersionedId("id1", 1), "destination2")

      Then("Distribution confirmation is received and is a failure")
      assert(Await.result(changeDistributted, to).isInstanceOf[Failure])

      And("Notifier is completed")
      assert(listenerResponse.contains("valueone@id1@1"))

    }

    scenario("Does not confirm distribution if listener times out") {

      Given("A new shard storing string values named test3 with always timing out listener")
      val protocol = SatelliteStateActor.startSharded[String](Settings("test3", timeoutListener))(service.clusteringShardingExtension)

      When("state changing entity id1 to version 1 and value 'valueone'")
      val change1Status = protocol.stateChanged(VersionedId("id1", 1), "valueone", "destination1")

      Then("Version returned should have value == 0")
      assert(Await.result(change1Status, to).isInstanceOf[Success])

      When("Confirm Distribution is send")
      val changeDistributted = protocol.stateDistributed(VersionedId("id1", 1), "destination2")

      Then("Distribution confirmation is received and is a failure")
      assert(Await.result(changeDistributted, to).isInstanceOf[Failure])

      And("Notifier is completed")
      assert(listenerResponse.contains("valueone@id1@1"))

    }

    scenario("Can handle proper changeState - changeDistributed flow") {


      Given("A new shard storing string values named test1")
      val protocol = SatelliteStateActor.startSharded[String](Settings("test1", successListener))(service.clusteringShardingExtension)

      When("state changing entity id1 to version 1 and value 'valueone'")
      val change1Status = protocol.stateChanged(VersionedId("id1", 1), "valueone", "destination1")

      Then("Version returned should have value == 0")
      assert(Await.result(change1Status, to).isInstanceOf[Success])

      When("Confirm Distribution is send")
      val changeDistributted = protocol.stateDistributed(VersionedId("id1", 1), "destination2")

      Then("Distribution confirmation is received")
      assert(Await.result(changeDistributted, to).isInstanceOf[Success])

      And("Notifier is completed")
      assert(listenerResponse.contains("valueone@id1@1"))

      listenerResponse = None

      When("We repeat messages")

      When("state changing entity id1 to version 1 and value 'valueone'")
      val change1Status1 = protocol.stateChanged(VersionedId("id1", 1), "valueone", "destination1")

      Then("Version returned should have value == 0")
      assert(Await.result(change1Status1, to).isInstanceOf[Success])

      When("Confirm Distribution is send")
      val changeDistributted1 = protocol.stateDistributed(VersionedId("id1", 1), "destination2")

      Then("Distribution confirmation is received")
      assert(Await.result(changeDistributted1, to).isInstanceOf[Success])

      And("Notifier is completed")
      assert(listenerResponse.contains("valueone@id1@1"))


    }
  }
  val successListener: SatelliteStateListener[String] = new SatelliteStateListener[String] {
    override def configurationChanged(versionedId: VersionedId, value: String)
                                     (implicit executionContext: ExecutionContext): Future[Status] = {
      Future {
        listenerResponse = Some(value + "@" + versionedId)
        Success(0)
      }
    }
  }
  val failingListener: SatelliteStateListener[String] = new SatelliteStateListener[String] {
    override def configurationChanged(versionedId: VersionedId, value: String)
                                     (implicit executionContext: ExecutionContext): Future[Status] = {
      Future {
        listenerResponse = Some(value + "@" + versionedId)
        Failure(new Exception("!"))
      }
    }
  }
  val timeoutListener: SatelliteStateListener[String] = new SatelliteStateListener[String] {
    override def configurationChanged(versionedId: VersionedId, value: String)
                                     (implicit executionContext: ExecutionContext): Future[Status] = {
      listenerResponse = Some(value + "@" + versionedId)
      Future.never
    }
  }
  var listenerResponse: Option[String] = _

  override def withFixture(test: NoArgTest): Outcome = {
    listenerResponse = None
    super.withFixture(test)
  }
}