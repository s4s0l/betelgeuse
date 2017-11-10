/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.utils

import java.io.Closeable

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object TryWith extends TryWith {

}

trait TryWith {
  def tryWithCloseable[C <: Closeable, R](resource: => C)(f: C => R): Try[R] =
    Try(resource).flatMap(resourceInstance => {
      try {
        val returnValue = f(resourceInstance)
        Try(resourceInstance.close()).map(_ => returnValue)
      }
      catch {
        case NonFatal(exceptionInFunction) =>
          try {
            resourceInstance.close()
            Failure(exceptionInFunction)
          }
          catch {
            case NonFatal(exceptionInClose) =>
              exceptionInFunction.addSuppressed(exceptionInClose)
              Failure(exceptionInFunction)
          }
      }
    })

  def tryWithAutoCloseableWithException[C <: AutoCloseable, R](resource: => C)(f: C => R): R =
    tryWithAutoCloseable(resource)(f) match {
      case Success(x) => x
      case Failure(x) => throw x
    }

  def tryWithCloseableWithException[C <: Closeable, R](resource: => C)(f: C => R): R =
    tryWithCloseable(resource)(f) match {
      case Success(x) => x
      case Failure(x) => throw x
    }

  def tryWithAutoCloseable[C <: AutoCloseable, R](resource: => C)(f: C => R): Try[R] =
    Try(resource).flatMap(resourceInstance => {
      try {
        val returnValue = f(resourceInstance)
        Try(resourceInstance.close()).map(_ => returnValue)
      }
      catch {
        case NonFatal(exceptionInFunction) =>
          try {
            resourceInstance.close()
            Failure(exceptionInFunction)
          }
          catch {
            case NonFatal(exceptionInClose) =>
              exceptionInFunction.addSuppressed(exceptionInClose)
              Failure(exceptionInFunction)
          }
      }
    })


}