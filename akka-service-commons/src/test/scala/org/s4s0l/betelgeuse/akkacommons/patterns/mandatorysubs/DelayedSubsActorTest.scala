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

package org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs

import akka.actor.ActorRef
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor.Protocol.{PublicationResultNotOk, PublicationResultOk, PublishMessage}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor.{Listener, Settings}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class DelayedSubsActorTest extends BgTestService {

  val to: FiniteDuration = 1 second
  private val s = testWith(new BgService {})

  feature("Delayed subs acknowledges when all listeners are present") {
    scenario("Gets publication after future completes") {
      new WithService(s) {
        Given("A promise of listeners")
        private val promisedListeners = Promise[Seq[Listener[String, String]]]()
        private val delayedSubsActor = DelayedSubsActor.start(Settings("test1", promisedListeners.future))

        When("Promise completes with two always successful listeners")
        private val listenerOne = stub[Listener[String, String]]
        private val listenerTwo = stub[Listener[String, String]]
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultOk("1")))
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultOk("1")))
        promisedListeners.complete(util.Success(Seq(listenerOne, listenerTwo)))

        And("We send publication")
        delayedSubsActor.send(PublishMessage("1", "value"))

        Then("We expect an ack")
        testKit.expectMsg(to, PublicationResultOk("1"))
        And("All listeners where notified")
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)

      }
    }
    scenario("Gets publication before future completes") {
      new WithService(s) {
        Given("A promise of listeners")
        private val promisedListeners = Promise[Seq[Listener[String, String]]]()
        private val delayedSubsActor = DelayedSubsActor.start(Settings("test1", promisedListeners.future))
        private val listenerOne = stub[Listener[String, String]]
        private val listenerTwo = stub[Listener[String, String]]
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultOk("1")))
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultOk("1")))
        When("We send publication")

        delayedSubsActor.send(PublishMessage("1", "value"))

        Then("No ack is received")
        testKit.expectNoMsg(to)


        When("Promise completes with two always successful listeners")
        promisedListeners.complete(util.Success(Seq(listenerOne, listenerTwo)))


        Then("We expect an ack")
        testKit.expectMsg(PublicationResultOk("1"))

        And("All listeners where notified")
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)

      }
    }

    scenario("Does not notify when one listener fails") {
      new WithService(s) {
        Given("A promise of listeners, one fails")
        private val promisedListeners = Promise[Seq[Listener[String, String]]]()
        private val listenerOne = stub[Listener[String, String]]
        private val listenerTwo = stub[Listener[String, String]]
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.failed(new Exception("!")))
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultOk("1")))
        promisedListeners.complete(util.Success(Seq(listenerOne, listenerTwo)))
        private val delayedSubsActor = DelayedSubsActor.start(Settings("test1", promisedListeners.future))

        When("We send publication")

        delayedSubsActor.send(PublishMessage("1", "value"))

        Then("No ack is received")
        testKit.expectMsgClass(to, classOf[PublicationResultNotOk[String]])

        And("All listeners where notified")
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)

      }
    }

    scenario("Does not notify when one listener returns failure") {
      new WithService(s) {
        Given("A promise of listeners, one fails")
        private val promisedListeners = Promise[Seq[Listener[String, String]]]()
        private val listenerOne = stub[Listener[String, String]]
        private val listenerTwo = stub[Listener[String, String]]
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultNotOk("1", new Exception("!"))))
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).when(PublishMessage("1", "value"), *, *).returns(Future.successful(PublicationResultOk("1")))
        promisedListeners.complete(util.Success(Seq(listenerOne, listenerTwo)))
        private val delayedSubsActor = DelayedSubsActor.start(Settings("test1", promisedListeners.future))

        When("We send publication")

        delayedSubsActor.send(PublishMessage("1", "value"))

        Then("No ack is received")
        testKit.expectMsgClass(to, classOf[PublicationResultNotOk[String]])

        And("All listeners where notified")
        (listenerOne.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)
        (listenerTwo.onMessage(_: PublishMessage[String, String])(_: ExecutionContext, _: ActorRef)).verify(PublishMessage("1", "value"), *, *)

      }
    }
  }


}

object DelayedSubsActorTest {

}

