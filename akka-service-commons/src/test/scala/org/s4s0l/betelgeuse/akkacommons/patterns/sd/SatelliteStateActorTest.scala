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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRef
import akka.serialization.Serialization
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.NewVersionedValueListener.{NewVersionNotOk, NewVersionOk}
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.{BgSatelliteStateService, NewVersionedValueListener}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.ValidationError
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteProtocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteStateActorTest.Dummy
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.{GetValue, GetValueNotOk, GetValueOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.serialization._
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


  feature("Satellite State actor can persist anything handler gives it") {
    scenario("Object mapping example") {
      val idInTest = "id4asdfas"
      new WithService(my) {

        Given("A new shard storing string values named test1")
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.handlerObject.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 2))

        Then("Version returned should have value == 0")
        assert(Await.result(change1Status, to * 20).isInstanceOf[StateChangeOk])

        When("Confirm Distribution is send")
        private val changeDistributed = service.handlerObject.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation is received")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteOk])

      }
      When("We restart")
      restartServices()
      new WithService(my) {

        Then("We see previously stored data")
        private val gotValue = service.handlerObject.getValue(GetValue(VersionedId(s"$idInTest", 1)))
        assert(Await.result(gotValue, to * 2) == GetValueOk(VersionedId(s"$idInTest", 1), Dummy("valueOne")))

      }

    }
  }

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
        assert(handlerRequest.contains(s"valueOne"))

        listenerResponse = None
        handlerRequest = None

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
        And("Handler is not called")
        assert(handlerRequest.isEmpty)

      }

    }

    scenario("Can handle proper changeState even when hanges come quickly we do not let handler to work twice") {
      new WithService(my) {


        Given("A new shard storing string values named test1")
        private val idInTest = "id4xxxx"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne' done twice")
        private val change1StatusA = service.slowHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 4))
        private val change1StatusB = service.slowHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 4))

        Then("Should respond ok for both")
        assert(Await.result(change1StatusA, to * 4).isInstanceOf[StateChangeOk])
        assert(Await.result(change1StatusB, to).isInstanceOf[StateChangeOk])

        And("Handler should be called only once")
        assert(service.slowHandlerCounter.get() == 1)
      }

    }

    scenario("Handler reports validation error") {
      new WithService(my) {


        Given("A new shard storing string values named test1")
        private val idInTest = "id4aa"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.validationFailedHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 20))


        Then("Should respond with validation failed")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOkWithValidationError])

        And("Handler was called")
        assert(handlerRequest.contains("valueOne"))

        When("Confirm Distribution is send")
        private val changeDistributed = service.validationFailedHandler.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation does not complete ok")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteNotOk])

        And("Listener is not called as we did not store any value")
        assert(listenerResponse.isEmpty)


        listenerResponse = None
        handlerRequest = None

        When("We repeat messages")

        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status1 = service.validationFailedHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Should respond with validation failed - again")
        assert(Await.result(change1Status1, to * 2).isInstanceOf[StateChangeOkWithValidationError])

        And("Handler should not be called again")
        assert(handlerRequest.isEmpty)

      }

    }

    scenario("Handler rejects value") {
      new WithService(my) {


        Given("A new shard storing string values named test1")
        private val idInTest = "id4aaxx"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.rejectingHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 2))

        Then("Should respond with ok")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOk])

        And("Handler was called")
        assert(handlerRequest.contains("valueOne"))

        When("Confirm Distribution is send")
        private val changeDistributed = service.rejectingHandler.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation completes ok")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteOk])

        And("Notifier is not bothered")
        assert(listenerResponse.isEmpty)

        When("We request value that was previously confirmed")
        private val getRsp = service.rejectingHandler.getValue(GetValue(VersionedId(s"$idInTest", 1)))

        Then("value will be missing")
        assert(Await.result(getRsp, to).isInstanceOf[GetValueNotOk[String]])

        listenerResponse = None
        handlerRequest = None

        When("We repeat messages")

        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status1 = service.rejectingHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Should respond with ok - again")
        assert(Await.result(change1Status1, to * 2).isInstanceOf[StateChangeOk])

        And("Handler should not be called again")
        assert(handlerRequest.isEmpty)

      }

    }

    scenario("Handler fails value") {
      new WithService(my) {


        Given("A new shard storing string values named test1")
        private val idInTest = "id4aayy"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.failedHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 2))

        Then("Should respond with not ok")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeNotOk])

        And("Handler was called")
        assert(handlerRequest.contains("valueOne"))

        When("Confirm Distribution is send")
        private val changeDistributed = service.failedHandler.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation completes not ok")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is bothered")
        assert(listenerResponse.isEmpty)

        listenerResponse = None
        handlerRequest = None

        When("We repeat messages")

        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status1 = service.failedHandler.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Should respond with ok - again")
        assert(Await.result(change1Status1, to * 2).isInstanceOf[StateChangeNotOk])

        And("Handler should be called again")
        assert(handlerRequest.contains("valueOne"))

      }

    }


  }

  feature("Satellite State actor is an VersionedEntityActor with ability to confirm distribution using Message pattern protocol also") {

    scenario("Handler reports validation error using Message pattern protocol also") {
      new WithService(my) {
        private implicit val serializer: Serialization = service.serializer

        Given("A new shard storing string values named test1")
        private val idInTest = "id4aamp"
        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status = service.validationFailedHandler.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to * 20))


        Then("Should respond with validation failed")
        assert(Await.result(change1Status, to * 2).isInstanceOf[StateChangeOkWithValidationError])

        And("Handler was called")
        assert(handlerRequest.contains("valueOne"))

        When("Confirm Distribution is send")
        private val changeDistributed = service.validationFailedHandler.asRemote.distributionComplete(DistributionComplete(VersionedId(s"$idInTest", 1), to))

        Then("Distribution confirmation does not complete ok")
        assert(Await.result(changeDistributed, to).isInstanceOf[DistributionCompleteNotOk])

        And("Notifier is completed")
        assert(listenerResponse.isEmpty)

        listenerResponse = None
        handlerRequest = None

        When("We repeat messages")

        When(s"state changing entity $idInTest to version 1 and value 'valueOne'")
        private val change1Status1 = service.validationFailedHandler.asRemote.stateChange(StateChange(VersionedId(s"$idInTest", 1), "valueOne", to))

        Then("Should respond with validation failed - again")
        assert(Await.result(change1Status1, to * 2).isInstanceOf[StateChangeOkWithValidationError])

        And("Handler should not be called again")
        assert(handlerRequest.isEmpty)

      }

    }


    scenario("Does not confirm distribution if value was not introduced before using Message pattern protocol also") {
      new WithService(my) {
        private implicit val serializer: Serialization = service.serializer
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
        private implicit val serializer: Serialization = service.serializer
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
        private implicit val serializer: Serialization = service.serializer
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
        private implicit val serializer: Serialization = service.serializer

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
    with BgClusteringSharding
    with BgClusteringReceptionist
    with BgSatelliteStateService {

    lazy val rejectingHandler: SatelliteStateActor.Protocol[String, String] = {
      val context = createSatelliteStateFactory[String, String]("SatelliteStateActorTestRejectingHandler", (d: String) => {
        handlerRequest = Some(d)
        Left(None)
      })
      context.addGlobalListener("l", successListener)
      context.enable()
      context.satelliteStateActor
    }

    lazy val handlerObject: SatelliteStateActor.Protocol[String, Dummy] = {
      val context = createSatelliteStateFactory[String, Dummy]("SatelliteStateActorTestObjectHandler", (d: String) => {
        Left(Some(Dummy(d)))
      })
      context.addGlobalListener("l", new NewVersionedValueListener[Dummy] {
        override def onNewVersionAsk(versionedId: VersionedId, aValue: Dummy)(implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[NewVersionedValueListener.NewVersionResult] =
          Future.successful(NewVersionOk(versionedId))
      })
      context.enable()
      context.satelliteStateActor
    }

    @volatile var slowHandlerCounter: AtomicInteger = new AtomicInteger(0)
    lazy val slowHandler: SatelliteStateActor.Protocol[String, String] = {

      val context = createSatelliteStateFactory[String, String]("SatelliteStateActorTestSlowHandler", (d: String) => {
        handlerRequest = Some(d)
        slowHandlerCounter.incrementAndGet()
        Thread.sleep(2000)
        Left(None)
      })
      context.addGlobalListener("l", successListener)
      context.enable()
      context.satelliteStateActor
    }

    lazy val validationFailedHandler: SatelliteStateActor.Protocol[String, String] = {
      val context = createSatelliteStateFactory[String, String]("SatelliteStateActorTestValidationFailedHandler", (d: String) => {
        handlerRequest = Some(d)
        Right(ValidationError(Seq("No!")))
      })
      context.addGlobalListener("l", successListener)
      context.enable()
      context.satelliteStateActor
    }

    lazy val failedHandler: SatelliteStateActor.Protocol[String, String] = {
      val context = createSatelliteStateFactory[String, String]("SatelliteStateActorTestFailedHandler", (d: String) => {
        handlerRequest = Some(d)
        throw new Exception("!")
      })
      context.addGlobalListener("l", successListener)
      context.enable()
      context.satelliteStateActor
    }


    lazy val successSatellite: SatelliteStateActor.Protocol[String, String] = {
      val context = createSatelliteStateFactory[String, String]("SatelliteStateActorTestSuccess", (d: String) => {
        handlerRequest = Some(d)
        Left(Some(d))
      })
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

  var handlerRequest: Option[String] = _

  override def withFixture(test: NoArgTest): Outcome = {
    listenerResponse = None
    handlerRequest = None
    super.withFixture(test)
  }

}


object SatelliteStateActorTest {


  case class Dummy(value: String) extends JacksonJsonSerializable

}
