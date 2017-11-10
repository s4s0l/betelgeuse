/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.s4s0l.betelgeuse.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * @author Marcin Wielgus
  */
trait FutureUtils {

  def futureToFutureTry[T](f: Future[T])(implicit executionContext:ExecutionContext): Future[Try[T]] =
    f.map(Success(_)).recover {
      case x => Failure(x)
    }

  def listOfFuturesToFutureOfList[T](l: Seq[Future[T]])(implicit executionContext:ExecutionContext): Future[Seq[T]] = {
    val eventualTriedTs: Future[Seq[Try[T]]] = Future.sequence(l.map(futureToFutureTry))
    eventualTriedTs.map { seq =>
      seq.map {
        case Success(x) => x
        case Failure(f) => throw f
      }
    }
  }

  implicit def toRichFuture[T](f:Future[T]): RichFuture[T] = new RichFuture[T](f)
}


class RichFuture[T](f: Future[T]) {
  def mapAll[U](pf: PartialFunction[Try[T], U])(implicit excecutionContext:ExecutionContext): Future[U] = {
    val p = Promise[U]()
    f.onComplete(r => p.complete(Try(pf(r))))
    p.future
  }
}