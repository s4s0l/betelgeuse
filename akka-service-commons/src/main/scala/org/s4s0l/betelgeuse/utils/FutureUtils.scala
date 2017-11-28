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

package org.s4s0l.betelgeuse.utils

import akka.actor.Status
import akka.actor.Status.Status

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * @author Marcin Wielgus
  */
trait FutureUtils {

  def listOfFuturesToFutureOfList[T](l: Seq[Future[T]])(implicit executionContext: ExecutionContext): Future[Seq[T]] = {
    val eventualTriedTs: Future[Seq[Try[T]]] = Future.sequence(l.map(futureToFutureTry))
    eventualTriedTs.map { seq =>
      seq.map {
        case Success(x) => x
        case Failure(f) => throw f
      }
    }
  }

  def futureToFutureTry[T](f: Future[T])(implicit executionContext: ExecutionContext): Future[Try[T]] =
    f.map(Success(_)).recover {
      case x => Failure(x)
    }

  implicit def toRichFuture[T](f: Future[T]): RichFuture[T] = new RichFuture[T](f)
}


class RichFuture[T](wrappedFuture: Future[T]) {

  /**
    * Allows mapping at the same time success or failure.
    * but see https://stackoverflow.com/questions/23043679/map-a-future-for-both-success-and-failure
    * from scala 2.11 we got [[Future.transform()]] but it leaves us with Try at the and.
    *
    * also map.revocer seems like a bettern pattern
    *
    */
  def mapAll[U](pf: PartialFunction[Try[T], U])(implicit excecutionContext: ExecutionContext): Future[U] = {
    val p = Promise[U]()
    wrappedFuture.onComplete(r => p.complete(Try(pf(r))))
    p.future
  }


  def recoverToAkkaStatus(implicit executionContext: ExecutionContext): Future[Status] = {
    wrappedFuture
      .map {
        case status: Status => status.asInstanceOf[Status]
        case x => Status.Success(x)
      }
      .recover { case ex: Throwable => Status.Failure(ex) }
  }

}