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

import akka.actor.{Actor, Props}
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.Protocol.{Ack, PublishMessage, Subscribe, SubscribeAck}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.Settings
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActorTest.EchoActor
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaServiceSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class MandatorySubsActorTest extends BetelgeuseAkkaServiceSpecLike[BetelgeuseAkkaService] {

  override def createService(): BetelgeuseAkkaService = new BetelgeuseAkkaService {}

  feature("Mandatory subs actor that can broadcast messages to its subscribers confirming if all mandatory subscribers confirmed it") {
    scenario("All subscribers present") {
      val subs = MandatorySubsActor.start(Settings("test1", List("one", "two"), MandatorySubsActor.defaultMessageForwarder))
      val one = service.system.actorOf(Props(new EchoActor("ONE")))
      val two = service.system.actorOf(Props(new EchoActor("TWO")))

      assert(Await.result(subs.subscribe(Subscribe("one", one)), 1 second) == SubscribeAck("one", one))
      assert(Await.result(subs.subscribe(Subscribe("two", two)), 1 second) == SubscribeAck("two", two))

      subs.send(PublishMessage("1", "hello"))(self)

      testKit.expectMsg(Ack("1"))
      assert(MandatorySubsActorTest.queue.contains("ONE:hello"))
      assert(MandatorySubsActorTest.queue.contains("TWO:hello"))

    }

    scenario("One subscriber missing") {
      assert(condition = false)
    }

    scenario("Additional subscriber over mandatory ones") {
      assert(condition = false)
    }

    scenario("No subscribers at all") {
      assert(condition = false)
    }

    scenario("No mandatory subscribers") {
      assert(condition = false)
    }

    scenario("Subscriber fails") {
      assert(condition = false)
    }
  }
}


object MandatorySubsActorTest {

  val queue: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]

  class EchoActor(name: String) extends Actor {
    override def receive: Receive = {
      case a =>
        queue.add(name + ":" + a.toString)
        sender() ! a
    }
  }

}
