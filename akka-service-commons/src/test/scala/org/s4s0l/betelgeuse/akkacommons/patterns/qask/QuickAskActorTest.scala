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

package org.s4s0l.betelgeuse.akkacommons.patterns.qask

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.qask.QuickAskActorTest.Question
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

/**
  * @author Marcin Wielgus
  */

class QuickAskActorTest extends BgTestService {

  private val echoingService = testWith(new BgService {
    lazy val echoActor: ActorRef = system.actorOf(Props(new Actor {
      override def receive: Receive = {
        case x => sender() ! x
      }
    }))

    lazy val fAskingActor: ActorRef = system.actorOf(Props(new Actor with ActorLogging with QuickAskActor {
      implicit val to: Timeout = 10 seconds
      var count: AtomicInteger = new AtomicInteger(0)
      var promise: Promise[Boolean] = _


      override def receive: Receive = {
        case ("fAsk", times: Int, p: Promise[_]) =>
          promise = p.asInstanceOf[Promise[Boolean]]
          count = new AtomicInteger(0)
          (1 to times).foreach { it =>
            fAsk(echoActor, Question(it)).onComplete {
              _ =>
                if (count.incrementAndGet() >= times) {
                  promise.complete(Success(true))
                }
            }
          }
        case ("ask", times: Int, p: Promise[_]) =>
          promise = p.asInstanceOf[Promise[Boolean]]
          count = new AtomicInteger(0)
          (1 to times).foreach { it =>
            (echoActor ? Question(it)).onComplete {
              _ =>
                if (count.incrementAndGet() >= times) {
                  promise.complete(Success(true))
                }
            }
          }

      }
    }))
  })

  feature("Fast asking is like asking but without temporary actor") {
    scenario("Handling of responses looks like regular future") {
      new WithService(echoingService) {

      }
    }
  }


}


object QuickAskActorTest {

  case class Answer(id: Int)

  case class Question(id: Int) extends QuickAskActor.Question[Answer] {
    override def isAnsweredBy(answer: Answer): Boolean = {
      answer.id == id
    }
  }

}