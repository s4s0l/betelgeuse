/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 12:28
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