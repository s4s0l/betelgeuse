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

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.qask.QuickAskActor._

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
@deprecated("Do not use!")
trait QuickAskActor {
  actor: Actor with ActorLogging =>

  private var nextId: Long = 0
  private val questionsAsked: mutable.Map[Any, AskedQuestion[Any]] = mutable.Map()

  def fAskPf: Receive = {
    case AskedQuestionTimeout(id) =>
      handleFAskTimeout(id)
    case answer: Answer =>
      handleFAskAnswer(answer)
  }


  implicit def defaultAnswerInterceptor[A](implicit classTag: ClassTag[A]): AnswerInterceptor[A, A] = new AnswerInterceptor[A, A](classTag, identity)

  private def handleFAskTimeout(id: Any): Unit = {
    val asked = questionsAsked.remove(id)
    asked.foreach { x =>
      x.promise.complete(Failure(new Exception("FAsk timed out!")))
    }
  }

  private def handleFAskAnswer(answer: Answer): Unit = {
    val correlationId = answer.getCorrelationId
    val mostProbable = questionsAsked.remove(correlationId)
    mostProbable.foreach { it =>
      it.promise.complete(Success(answer))
      it.cancellable.cancel()
    }
    if (mostProbable.isEmpty) {
      log.debug("Got answer that cannot be handled, from {}, of type {}", sender(), answer.getClass.getName)
    }
  }

  def getNextId: Long = {
    nextId = nextId + 1
    nextId
  }

  def fAsk[A](to: ActorRef, q: Question[A])
             (implicit interceptor: AnswerInterceptor[_, A], timeout: Timeout)
  : Future[A] = {
    to.tell(q, actor.self)
    val promise = Promise[A]()
    val id = q.getMessageId
    val cancellable = context.system.scheduler.scheduleOnce(timeout.duration, self, AskedQuestionTimeout(id))(context.dispatcher, self)
    val value = AskedQuestion(id, q, interceptor.asInstanceOf[AnswerInterceptor[Any, A]], timeout, System.currentTimeMillis(), promise, cancellable)
    questionsAsked.put(id, value.asInstanceOf[AskedQuestion[Any]])

    //    addFAskAnswerType(interceptor.classTag)
    promise.future
  }


}

object QuickAskActor {

  trait Question[A] {
    def isAnsweredBy(answer: A): Boolean

    def getMessageId: Any

  }

  trait Answer {
    def getCorrelationId: Any
  }

  class AnswerInterceptor[A, B](val classTag: ClassTag[A],
                                transform: A => B) {

    def shouldHandle(a: A): Boolean = classTag.runtimeClass.isInstance(a)

    def toAnswer(a: A): B = transform.apply(a)
  }

  private case class AskedQuestion[A](
                                       key: Any,
                                       question: Question[A],
                                       interceptor: AnswerInterceptor[Any, A],
                                       timeout: Timeout,
                                       askedWhen: Long,
                                       promise: Promise[A],
                                       cancellable: Cancellable)

  private case class AskedQuestionTimeout(key: Any)


}
