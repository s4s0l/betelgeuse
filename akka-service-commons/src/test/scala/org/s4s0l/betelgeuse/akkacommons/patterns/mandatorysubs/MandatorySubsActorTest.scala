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

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.Status.Status
import akka.actor.{Actor, Props}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.Protocol.{Ack, PublishMessage, Subscribe, SubscribeAck}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.{MessageForwarder, MessageForwarderContext, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActorTest.EchoActor
import org.s4s0l.betelgeuse.akkacommons.test.BgServiceSpecLike
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class MandatorySubsActorTest extends BgServiceSpecLike[BgService] {

  override def createService(): BgService = new BgService {}

  val to: FiniteDuration = 5 second
  implicit val timeUnit: Timeout = to


  feature("Mandatory subs actor that can broadcast messages to its subscribers confirming if all mandatory subscribers confirmed it") {
    scenario("All subscribers present") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test0", List("one", "two"), MandatorySubsActor.defaultMessageForwarder))
      When("Both of them subscribe")
      val one = service.system.actorOf(Props(new EchoActor("ONE")))
      val two = service.system.actorOf(Props(new EchoActor("TWO")))

      Then("Subscription is acknowledged for each of them")
      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello0"))(self)

      Then("Ack arrives back")
      testKit.expectMsg(Ack("1"))
      And("All subscribers got the message")
      assert(MandatorySubsActorTest.queue.contains("ONE:hello0"))
      assert(MandatorySubsActorTest.queue.contains("TWO:hello0"))
      assert(MandatorySubsActorTest.queue.size() == 2)

    }

    scenario("One subscriber missing") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test2", List("one", "two"),
        MandatorySubsActor.defaultMessageForwarder, 1 second))
      When("Only one subscribes")
      val one = service.system.actorOf(Props(new EchoActor("ONE")))

      Then("Subscription is acknowledged.")
      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello2"))(self)

      Then("Ack does not arrive back in 2*timeout time")
      testKit.expectNoMsg(2 second)
      And("existing subscriber gets the message anyway")
      assert(MandatorySubsActorTest.queue.size() == 1)
      assert(MandatorySubsActorTest.queue.contains("ONE:hello2"))

    }

    scenario("Additional subscriber over mandatory ones") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test3", List("one", "two"), MandatorySubsActor.defaultMessageForwarder))
      When("Both of them subscribe")
      val one = service.system.actorOf(Props(new EchoActor("ONE")))
      val two = service.system.actorOf(Props(new EchoActor("TWO")))
      And("Another one")
      val three = service.system.actorOf(Props(new EchoActor("THREE")))

      Then("Subscription is acknowledged for each of them")
      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))
      assert(Await.result(subs.subscribe(Subscribe("three", three)), 1 second) == SubscribeAck("three", three))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello3"))(self)

      Then("Ack arrives back")
      testKit.expectMsg(Ack("1"))
      And("All subscribers got the message together with not mandatory one")
      assert(MandatorySubsActorTest.queue.contains("ONE:hello3"))
      assert(MandatorySubsActorTest.queue.contains("TWO:hello3"))
      assert(MandatorySubsActorTest.queue.contains("THREE:hello3"))
      assert(MandatorySubsActorTest.queue.size() == 3)

    }

    scenario("No subscribers at all") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test4", List("one", "two"), MandatorySubsActor.defaultMessageForwarder, 1 second))
      When("No subscribers register")

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello0"))(self)

      Then("No Ack arrives back")
      testKit.expectNoMsg(2 second)
    }

    scenario("No mandatory subscribers") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test5", List("one", "two"), MandatorySubsActor.defaultMessageForwarder, 1 second))
      When("None of them subscribe but some other")
      val three = service.system.actorOf(Props(new EchoActor("THREE")))
      assert(Await.result(subs.subscribe(Subscribe("three", three)), 1 second) == SubscribeAck("three", three))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello5"))(self)

      Then("No Ack arrives back")
      testKit.expectNoMsg(2 second)
      And("All subscribers got the message")
      assert(MandatorySubsActorTest.queue.contains("THREE:hello5"))
      assert(MandatorySubsActorTest.queue.size() == 1)
    }

    scenario("One subscriber does not respond") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test1", List("one", "two"), MandatorySubsActor.defaultMessageForwarder, 1 second))
      When("Both of them subscribe but one never responds")
      val one = service.system.actorOf(Props(new EchoActor("ONE")))
      val two = service.system.actorOf(Props(new EchoActor("TWO", false)))

      Then("Subscription is acknowledged for each of them")
      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello1"))(self)

      Then("Ack does not arrive back in 2*timeout time")
      testKit.expectNoMsg(2 second)
      And("both subsctibers got the message anyway")
      assert(MandatorySubsActorTest.queue.contains("ONE:hello1"))
      assert(MandatorySubsActorTest.queue.contains("TWO:hello1"))
    }

    scenario("Subscriber fails") {
      Given("Mandatory subs actor with two required subscribers, and for one of them forwarder will return failure")
      val failingProvider: MessageForwarder = new MessageForwarder {
        override def forward(publishMessage: PublishMessage, context: MessageForwarderContext)(implicit ec: ExecutionContext): Future[Status] = {
          if (context.key == "one")
            Future.failed(new Exception("ex"))
          else MandatorySubsActor.defaultMessageForwarder.forward(publishMessage, context)
        }
      }
      val subs = MandatorySubsActor.start(Settings("test7", List("one", "two"), failingProvider, 1 second))
      When("Both of them subscribe but one never responds")
      val one = service.system.actorOf(Props(new EchoActor("ONE")))
      val two = service.system.actorOf(Props(new EchoActor("TWO", false)))

      Then("Subscription is acknowledged for each of them")
      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello1"))(self)

      Then("Ack does not arrive back in 2*timeout time")
      testKit.expectNoMsg(2 second)
      And("only one subscriber got the message")
      assert(MandatorySubsActorTest.queue.contains("TWO:hello1"))
      assert(MandatorySubsActorTest.queue.size() == 1)

    }

    scenario("Repeated subscription") {
      Given("Mandatory subs actor with two required subscribers")
      val subs = MandatorySubsActor.start(Settings("test6", List("one", "two"), MandatorySubsActor.defaultMessageForwarder))
      When("Both of them subscribe")
      val one = service.system.actorOf(Props(new EchoActor("ONE")))
      val two = service.system.actorOf(Props(new EchoActor("TWO")))


      Then("Subscription is acknowledged for each of them")
      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))

      And("One of them registers twice")
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))

      When("Sending publication message")
      subs.send(PublishMessage("1", "hello0"))(self)

      Then("Ack arrives back")
      testKit.expectMsg(Ack("1"))
      And("All subscribers got the message")
      assert(MandatorySubsActorTest.queue.contains("ONE:hello0"))
      assert(MandatorySubsActorTest.queue.contains("TWO:hello0"))
      And("There was no duplicated acks")
      assert(MandatorySubsActorTest.queue.size() == 2)
    }
  }

  override def withFixture(test: NoArgTest): Outcome = {
    try {
      super.withFixture(test)
    } finally {
      MandatorySubsActorTest.queue.clear()
    }
  }
}


object MandatorySubsActorTest {

  val queue: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]

  class EchoActor(name: String, respond: Boolean = true) extends Actor {
    override def receive: Receive = {
      case a =>
        queue.add(name + ":" + a.toString)
        if (respond)
          sender() ! a
    }
  }

}
