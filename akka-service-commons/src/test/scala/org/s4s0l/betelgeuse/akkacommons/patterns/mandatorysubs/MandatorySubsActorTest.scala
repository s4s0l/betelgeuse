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

package org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{Actor, ActorRef, Props}
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.Protocol.{Publish, PublishNotOk, PublishOk, PublishResult, Subscribe, SubscribeOk}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.{MessageForwarder, MessageForwarderContext, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActorTest.EchoActor
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class MandatorySubsActorTest extends BgTestService {

  private val s = testWith(new BgService {})

  s.to = 15.second

  s.timeout = s.to


  feature("Mandatory subs actor that can broadcast messages to its subscribers confirming if all mandatory subscribers confirmed it") {
    scenario("All subscribers present") {
      new WithService(s) {

        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start[String, String](Settings("test0", List("one", "two")
          , MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("Both of them subscribe")
        private val one = service.system.actorOf(Props(new EchoActor("ONE")))
        private val two = service.system.actorOf(Props(new EchoActor("TWO")))

        Then("Subscription is acknowledged for each of them")
        assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeOk("one"))
        assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeOk("two"))

        When("Sending publication message")
        subs.send(Publish("1", "hello0", to))

        Then("Ack arrives back")
        testKit.expectMsg(PublishOk("1"))
        And("All subscribers got the message")
        assert(MandatorySubsActorTest.queue.contains("ONE:hello0"))
        assert(MandatorySubsActorTest.queue.contains("TWO:hello0"))
        assert(MandatorySubsActorTest.queue.size() == 2)
      }
    }

    scenario("One subscriber missing") {
      new WithService(s) {

        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start(Settings("test2", List("one", "two")
          , MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("Only one subscribes")
        private val one = service.system.actorOf(Props(new EchoActor("ONE")))

        Then("Subscription is acknowledged.")
        assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeOk("one"))

        When("Sending publication message")
        subs.send(Publish("1", "hello2", to))

        Then("Ack does not arrive back in 2*timeout time")
        testKit.expectMsgClass(2 second, classOf[PublishNotOk[String]])
        And("existing subscriber gets the message anyway")
        assert(MandatorySubsActorTest.queue.size() == 1)
        assert(MandatorySubsActorTest.queue.contains("ONE:hello2"))

      }
    }

    scenario("Additional subscriber over mandatory ones") {
      new WithService(s) {
        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start(Settings("test3", List("one", "two"),
          MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("Both of them subscribe")
        private val one = service.system.actorOf(Props(new EchoActor("ONE")))
        private val two = service.system.actorOf(Props(new EchoActor("TWO")))
        And("Another one")
        private val three = service.system.actorOf(Props(new EchoActor("THREE")))

        Then("Subscription is acknowledged for each of them")
        assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeOk("one"))
        assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeOk("two"))
        assert(Await.result(subs.subscribe(Subscribe("three", three)), 1 second) == SubscribeOk("three"))

        When("Sending publication message")
        subs.send(Publish("1", "hello3", to))

        Then("Ack arrives back")
        testKit.expectMsg(PublishOk("1"))
        And("All subscribers got the message together with not mandatory one")
        assert(MandatorySubsActorTest.queue.contains("ONE:hello3"))
        assert(MandatorySubsActorTest.queue.contains("TWO:hello3"))
        assert(MandatorySubsActorTest.queue.contains("THREE:hello3"))
        assert(MandatorySubsActorTest.queue.size() == 3)
      }
    }

    scenario("No subscribers at all") {
      new WithService(s) {
        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start(Settings("test4", List("one", "two"),
          MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("No subscribers register")

        When("Sending publication message")
        subs.send(Publish("1", "hello0", to))

        Then("No Ack arrives back")
        assert(testKit.expectMsgClass(classOf[PublishNotOk[String]]).correlationId == "1")

      }
    }

    scenario("No mandatory subscribers") {
      new WithService(s) {
        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start(Settings("test5", List("one", "two"),
          MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("None of them subscribe but some other")
        private val three = service.system.actorOf(Props(new EchoActor("THREE")))
        assert(Await.result(subs.subscribe(Subscribe("three", three)), 1 second) == SubscribeOk("three"))

        When("Sending publication message")
        subs.send(Publish("1", "hello5", to))

        Then("No Ack arrives back")
        assert(testKit.expectMsgClass(classOf[PublishNotOk[String]]).correlationId == "1")
        And("All subscribers got the message")
        assert(MandatorySubsActorTest.queue.contains("THREE:hello5"))
        assert(MandatorySubsActorTest.queue.size() == 1)
      }
    }

    scenario("One subscriber does not respond") {
      new WithService(s) {
        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start(Settings("test1", List("one", "two"),
          MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("Both of them subscribe but one never responds")
        private val one = service.system.actorOf(Props(new EchoActor("ONE")))
        private val two = service.system.actorOf(Props(new EchoActor("TWO", false)))

        Then("Subscription is acknowledged for each of them")
        assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeOk("one"))
        assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeOk("two"))

        When("Sending publication message")
        subs.send(Publish("1", "hello1", 3.seconds))

        Then("Ack does not arrive back in 2*timeout time")

        assert(testKit.expectMsgClass(7.seconds, classOf[PublishNotOk[String]]).correlationId == "1")
        And("both subscribers got the message anyway")
        assert(MandatorySubsActorTest.queue.contains("ONE:hello1"))
        assert(MandatorySubsActorTest.queue.contains("TWO:hello1"))
      }
    }

    scenario("Subscriber fails") {
      new WithService(s) {
        Given("Mandatory subs actor with two required subscribers, and for one of them forwarder will return failure")
        private val failingProvider: MessageForwarder[String, String] = new MessageForwarder[String, String] {
          override def forwardAsk(pm: Publish[String, String], context: MessageForwarderContext[String, String])
                                 (implicit executionContext: ExecutionContext, sender: ActorRef)
          : Future[PublishResult[String]] = {
            if (context.subscriptionKey == "one")
              Future.failed(new Exception("ex"))
            else MandatorySubsActor.defaultMessageForwarder.forwardAsk(pm, context)(executionContext, sender)
          }
        }
        private val subs = MandatorySubsActor.start(Settings("test7", List("one", "two"), failingProvider))
        When("Both of them subscribe but one never responds")
        private val one = service.system.actorOf(Props(new EchoActor("ONE")))
        private val two = service.system.actorOf(Props(new EchoActor("TWO", false)))

        Then("Subscription is acknowledged for each of them")
        assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeOk("one"))
        assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeOk("two"))

        When("Sending publication message")
        subs.send(Publish("1", "hello1", 3.second))

        Then("Ack does not arrive back in 2*timeout time")
        assert(testKit.expectMsgClass(4.seconds, classOf[PublishNotOk[String]]).correlationId == "1")
        And("only one subscriber got the message")
        assert(MandatorySubsActorTest.queue.contains("TWO:hello1"))
        assert(MandatorySubsActorTest.queue.size() == 1)
      }
    }

    scenario("Repeated subscription") {
      new WithService(s) {
        Given("Mandatory subs actor with two required subscribers")
        private val subs = MandatorySubsActor.start(Settings("test6", List("one", "two"),
          MandatorySubsActor.defaultMessageForwarder[String, String]))
        When("Both of them subscribe")
        private val one = service.system.actorOf(Props(new EchoActor("ONE")))
        private val two = service.system.actorOf(Props(new EchoActor("TWO")))


        Then("Subscription is acknowledged for each of them")
        assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeOk("one"))
        assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeOk("two"))

        And("One of them registers twice")
        assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeOk("two"))

        When("Sending publication message")
        subs.send(Publish("1", "hello0", to))

        Then("Ack arrives back")
        testKit.expectMsg(PublishOk("1"))
        And("All subscribers got the message")
        assert(MandatorySubsActorTest.queue.contains("ONE:hello0"))
        assert(MandatorySubsActorTest.queue.contains("TWO:hello0"))
        And("There was no duplicated acknowledgements")
        assert(MandatorySubsActorTest.queue.size() == 2)
      }
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
