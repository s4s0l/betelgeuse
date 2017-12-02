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
import org.s4s0l.betelgeuse.akkacommons.patterns.qask.QuickAskActor.{AnswerInterceptor, AskedQuestion, AskedQuestionTimeout, Question}

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
trait QuickAskActor {
  actor: Actor with ActorLogging =>

  private var nextId: Long = 0
  private var questionsAsked: List[AskedQuestion[Any]] = List()
  private var questionsSeen: Set[ClassTag[Any]] = Set()

  def fAskPf: Receive = {
    case AskedQuestionTimeout(id) =>
      handleFAskTimeout(id)
    case answer if isFAskAnswer(answer) =>
      handleFAskAnswer(answer)
  }

  private def isFAskAnswer(a: Any): Boolean = {
    questionsSeen.exists(_.runtimeClass.isInstance(a)) &&
      questionsAsked.exists(_.interceptor.shouldHandle(a))
  }

  implicit def defaultAnswerInterceptor[A](implicit classTag: ClassTag[A]): AnswerInterceptor[A, A] = new AnswerInterceptor[A, A](classTag, identity)

  private def handleFAskTimeout(id: Long): Unit = {
    val asked = questionsAsked.find(_.id == id)
    asked.foreach { x =>
      x.promise.complete(Failure(new Exception("FAsk timed out!")))
    }
    if (asked.isDefined) {
      questionsAsked = questionsAsked.filter(_.id == id)
    }
  }

  private def handleFAskAnswer(answer: Any): Unit = {
    val probableQuestions = questionsAsked
      .filter(q => q.interceptor.shouldHandle(answer))
    val mostProbable = probableQuestions.map(q => (q, q.interceptor.toAnswer(answer)))
      .filter(x => x._1.question.isAnsweredBy(x._2))
    mostProbable.foreach { it =>
      it._1.promise.complete(Success(it._2))
      it._1.cancellable.cancel()
    }
    val answered = mostProbable.map(_._1)
    if (mostProbable.isEmpty) {
      log.debug("Got answer that cannot be handled, from {}, of type {}", sender(), answer.getClass.getName)
    } else {
      questionsAsked = questionsAsked.filter(!answered.contains(_))
    }
  }

  def fAsk[A](to: ActorRef, q: Question[A])
             (implicit interceptor: AnswerInterceptor[_, A], timeout: Timeout)
  : Future[A] = {
    to.tell(q, actor.self)
    val promise = Promise[A]()
    nextId = nextId + 1
    val id = nextId
    val cancellable = context.system.scheduler.scheduleOnce(timeout.duration, self, AskedQuestionTimeout(id))(context.dispatcher, self)
    questionsAsked = AskedQuestion(id, q, interceptor.asInstanceOf[AnswerInterceptor[Any, A]], timeout, System.currentTimeMillis(), promise, cancellable).asInstanceOf[AskedQuestion[Any]] :: questionsAsked
    addFAskAnswerType(interceptor.classTag)
    promise.future
  }

  private def addFAskAnswerType[A, Q <: Question[A]](classTag: ClassTag[A]): Unit = {
    questionsSeen = questionsSeen + classTag.asInstanceOf[ClassTag[Any]]
  }

}

object QuickAskActor {

  trait Question[A] {
    def isAnsweredBy(answer: A): Boolean


  }

  class AnswerInterceptor[A, B](val classTag: ClassTag[A],
                                transform: A => B) {

    def shouldHandle(a: A): Boolean = classTag.runtimeClass.isInstance(a)

    def toAnswer(a: A): B = transform.apply(a)
  }

  private case class AskedQuestion[A](
                                       id: Long,
                                       question: Question[A],
                                       interceptor: AnswerInterceptor[Any, A],
                                       timeout: Timeout,
                                       askedWhen: Long,
                                       promise: Promise[A],
                                       cancellable: Cancellable)

  private case class AskedQuestionTimeout(is: Long)


}
