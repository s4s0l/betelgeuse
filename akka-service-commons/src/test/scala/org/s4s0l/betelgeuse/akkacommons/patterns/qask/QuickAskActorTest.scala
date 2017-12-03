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

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.qask.QuickAskActorTest.{Answer, Question, TAnswer, TQuestion}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.language.postfixOps
import scala.util.Success

/**
  * @author Marcin Wielgus
  */

class QuickAskActorTest extends BgTestService {


  val messageCount: Int = 1
  val runs: Int = 1

  private val echoingService = testWith(new BgService {
    lazy val echoActor: ActorRef = system.actorOf(Props(new Actor {
      override def receive: Receive = {
        case Question(id) => sender() ! Answer(id)
        case TQuestion(id) => sender() ! TAnswer(id)
        case x => sender() ! x
      }
    }))

    lazy val fAskingActor: ActorRef = system.actorOf(Props(new Actor with ActorLogging with QuickAskActor {
      implicit val to: Timeout = 10 seconds
      var count: AtomicInteger = new AtomicInteger(0)
      var promise: Promise[Boolean] = _
      var currentTimes = 0
      val questions: mutable.Map[Int, (Cancellable, TQuestion)] = mutable.Map()

      override def receive: Receive = fAskPf orElse {
        case TAnswer(id) =>
          val q = questions(id)
          q._1.cancel()
          questions.remove(id)
          if (count.incrementAndGet() >= currentTimes) {
            promise.complete(Success(true))
          }
        case ("tell", times: Int, p: Promise[_]) =>
          currentTimes = times
          promise = p.asInstanceOf[Promise[Boolean]]
          count = new AtomicInteger(0)
          (1 to times).foreach { it =>
            val q = TQuestion(it)
            val cancellable = context.system.scheduler.scheduleOnce(20 seconds, self, "Never should happen")
            questions.put(it, (cancellable, q))
            echoActor ! q
          }

        case ("fAsk", times: Int, p: Promise[_]) =>
          promise = p.asInstanceOf[Promise[Boolean]]
          count = new AtomicInteger(0)
          (1 to times).foreach { it =>
            fAsk(echoActor, Question(it)).onComplete {
              case Success(_) =>
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
              case Success(_) =>
                if (count.incrementAndGet() >= times) {
                  promise.complete(Success(true))
                }
            }
          }
        case ("pipe", times: Int, p: Promise[_]) =>
          import akka.pattern.pipe
          promise = p.asInstanceOf[Promise[Boolean]]
          count = new AtomicInteger(0)
          (1 to times).foreach { it =>
            val question = Question(it)
            (echoActor ? question).mapTo[Answer].map(x => (question, x, times)).pipeTo(self)
          }
        case (question: Question, a: Answer, times: Int) =>
          if (question.getMessageId == a.getCorrelationId)
            if (count.incrementAndGet() >= times) {
              promise.complete(Success(true))
            }
      }
    }))
  })

  feature("Fast asking is like asking but without temporary actor") {
    scenario("Warm Up") {
      new WithService(echoingService) {
        private val p = Promise[Boolean]()
        service.fAskingActor.tell(("fAsk", 1000, p), self)
        Await.result(p.future, 1 minute)

        private val p2 = Promise[Boolean]()
        service.fAskingActor.tell(("ask", 1000, p2), self)
        Await.result(p2.future, 1 minute)

        private val p3 = Promise[Boolean]()
        service.fAskingActor.tell(("pipe", 1000, p3), self)
        Await.result(p3.future, 1 minute)
      }
    }

    (1 to runs).foreach { it =>
      scenario(s"$it. Tell") {
        new WithService(echoingService) {
          private val p = Promise[Boolean]()
          service.fAskingActor.tell(("tell", messageCount, p), self)
          Await.result(p.future, 1 minute)

        }
      }
      scenario(s"$it. Ask") {
        new WithService(echoingService) {
          private val p = Promise[Boolean]()
          service.fAskingActor.tell(("ask", messageCount, p), self)
          Await.result(p.future, 1 minute)
        }
      }
      scenario(s"$it. Pipe") {
        new WithService(echoingService) {
          private val p = Promise[Boolean]()
          service.fAskingActor.tell(("pipe", messageCount, p), self)
          Await.result(p.future, 1 minute)
        }
      }
      scenario(s"$it. fAsk") {
        new WithService(echoingService) {
          private val p = Promise[Boolean]()
          service.fAskingActor.tell(("fAsk", messageCount, p), self)
          Await.result(p.future, 1 minute)
        }
      }
    }


  }


}


object QuickAskActorTest {

  case class TQuestion(id: Int)

  case class TAnswer(id: Int)

  case class Answer(getCorrelationId: Int) extends QuickAskActor.Answer

  case class Question(getMessageId: Int) extends QuickAskActor.Question[Answer] {


    override def isAnsweredBy(answer: Answer): Boolean = {
      answer.getCorrelationId == getMessageId
    }
  }

}