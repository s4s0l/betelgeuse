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

package org.s4s0l.betelgeuse.akkacommons.patterns.sd

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.NewVersionedValueListener.{NewVersionNotOk, NewVersionOk}
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.{BgSatelliteStateService, NewVersionedValueListener}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.serialization.{BgSerialization, BgSerializationJackson, SimpleSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.s4s0l.betelgeuse.akkacommons.test.{BgTestJackson, BgTestRoach}
import org.scalatest.Outcome

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class SatelliteStateActorTest extends
  BgTestRoach with BgTestJackson {


  feature("Satellite State actor is an VersionedEntityActor with ability to confirm distribution") {

    scenario("Does not confirm distribution if value was not introduced before") {
      new WithService(my) {

        Given("A new shard storing string values named testX")
        private val idInTest = "id1"

        When(s"stateDistributed is performed without prior stateChanged about entity $idInTest version 1")
        private val changeDistributed = service.successSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed, to * 2).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is not called")
        assert(listenerResponse.isEmpty)


        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val changeStatus = service.successSatellite.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(changeStatus, to).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed1 = service.successSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed1, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))
        listenerResponse = None

        When(s"stateDistributed is performed without prior stateChanged about entity $idInTest version 2")
        private val changeDistributed2 = service.successSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 2), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed2, to).isInstanceOf[DistributionCompleteNotOk])


        And("Notifier is not called")
        assert(listenerResponse.isEmpty)
      }

    }

    scenario("Does not confirm distribution if listener failed") {
      new WithService(my) {

        Given("A new shard storing string values named test3 with always failing listener")
        private val idInTest = "id2"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.failedSatellite.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.failedSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

      }
    }

    scenario("Does not confirm distribution if listener times out") {
      new WithService(my) {

        Given("A new shard storing string values named test3 with always timing out listener")
        private val idInTest = "id3"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.timeoutSatellite.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 2))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.timeoutSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed, to * 4).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

      }
    }

    scenario("Can handle proper changeState - changeDistributed flow") {
      new WithService(my) {


        Given("A new shard storing string values named test1")
        private val idInTest = "id4"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.successSatellite.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.successSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

        listenerResponse = None

        When("We repeat messages")

        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status1 = service.successSatellite.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status1, to).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed1 = service.successSatellite.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed1, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

      }

    }


  }

  feature("Satellite State actor is an VersionedEntityActor with ability to confirm distribution using Message pattern protocol also") {

    scenario("Does not confirm distribution if value was not introduced before using Message pattern protocol also") {
      new WithService(my) {
        private implicit val serializer: SimpleSerializer = service.simpleSerialization
        Given("A new shard storing string values named testX")
        private val idInTest = "mid1"

        When(s"stateDistributed is performed without prior stateChanged about entity $idInTest version 1")
        private val changeDistributed = service.successSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed, to * 2).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is not called")
        assert(listenerResponse.isEmpty)


        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val changeStatus = service.successSatellite.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(changeStatus, to).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed1 = service.successSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed1, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))
        listenerResponse = None

        When(s"stateDistributed is performed without prior stateChanged about entity $idInTest version 2")
        private val changeDistributed2 = service.successSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 2), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed2, to).isInstanceOf[DistributionCompleteNotOk])


        And("Notifier is not called")
        assert(listenerResponse.isEmpty)
      }

    }

    scenario("Does not confirm distribution if listener failed using Message pattern protocol also") {
      new WithService(my) {
        private implicit val serializer: SimpleSerializer = service.simpleSerialization
        Given("A new shard storing string values named test3 with always failing listener")
        private val idInTest = "mid2"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.failedSatellite.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.failedSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

      }
    }

    scenario("Does not confirm distribution if listener times out using Message pattern protocol also") {
      new WithService(my) {
        private implicit val serializer: SimpleSerializer = service.simpleSerialization
        Given("A new shard storing string values named test3 with always timing out listener")
        private val idInTest = "mid3"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.timeoutSatellite.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 2))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.timeoutSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received and is a failure")
        assert(Await.result(changeDistributed, to * 2).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

      }
    }

    scenario("Can handle proper changeState - changeDistributed flow using Message pattern protocol also") {
      new WithService(my) {
        private implicit val serializer: SimpleSerializer = service.simpleSerialization

        Given("A new shard storing string values named test1")
        private val idInTest = "mid4"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.successSatellite.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.successSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

        listenerResponse = None

        When("We repeat messages")

        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status1 = service.successSatellite.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status1, to).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed1 = service.successSatellite.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed1, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is completed")
        assert(listenerResponse.contains(s"valueOne@$idInTest@1"))

      }

    }


  }

  val successListener: NewVersionedValueListener[String] = new NewVersionedValueListener[String] {
    override def onNewVersionAsk(versionedId: VersionedId, aValue: String)
                                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[NewVersionedValueListener.NewVersionResult] = {
      Future {
        listenerResponse = Some(aValue + "@" + versionedId)
        NewVersionOk(versionedId)
      }
    }
  }
  val failingListener: NewVersionedValueListener[String] = new NewVersionedValueListener[String] {

    override def onNewVersionAsk(versionedId: VersionedId, aValue: String)
                                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[NewVersionedValueListener.NewVersionResult] = {
      Future {
        listenerResponse = Some(aValue + "@" + versionedId)
        NewVersionNotOk(versionedId, new Exception("!"))
      }
    }
  }
  val timeoutListener: NewVersionedValueListener[String] = new NewVersionedValueListener[String] {

    override def onNewVersionAsk(versionedId: VersionedId, aValue: String)
                                (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
    : Future[NewVersionedValueListener.NewVersionResult] = {
      listenerResponse = Some(aValue + "@" + versionedId)
      Future.never
    }
  }
  private val my = testWith(new BgPersistenceJournalRoach
    with BgSerialization
    with BgSerializationJackson
    with BgClusteringSharding
    with BgClusteringReceptionist
    with BgSatelliteStateService {

    lazy val successSatellite: SatelliteStateActor.Protocol[String, String] = {
      val context = createSimpleSatelliteStateFactory[String]("SatelliteStateActorTestSuccess")
      context.addGlobalListener("l", successListener)
      context.enable()
      context.satelliteStateActor
    }

    lazy val failedSatellite: SatelliteStateActor.Protocol[String, String] = {
      val context = createSimpleSatelliteStateFactory[String]("SatelliteStateActorTestFailing")
      context.addGlobalListener("l", failingListener)
      context.enable()
      context.satelliteStateActor
    }

    lazy val timeoutSatellite: SatelliteStateActor.Protocol[String, String] = {
      val context = createSimpleSatelliteStateFactory[String]("SatelliteStateActorTestTimeout")
      context.addGlobalListener("l", timeoutListener)
      context.enable()
      context.satelliteStateActor
    }


    override protected def onInitialized(): Unit = {
      successSatellite
      failedSatellite
      timeoutSatellite
    }
  })

  var listenerResponse: Option[String] = _

  override def withFixture(test: NoArgTest): Outcome = {
    listenerResponse = None
    super.withFixture(test)
  }
}