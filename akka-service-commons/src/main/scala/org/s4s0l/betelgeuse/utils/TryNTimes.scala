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

import org.slf4j.LoggerFactory

import scala.util.Random

/**
  * @author Marcin Wielgus
  */
object TryNTimes extends TryNTimes {
}

trait TryNTimes {
  private val LOGGER = LoggerFactory.getLogger(getClass)


  def tryNTimesExceptionFactory(msg: String): ((Int, Option[Exception])) => Exception = arg => {
    arg._2
      .map(ex => new RuntimeException(s"$msg. Unable to complete in ${arg._1} attempt, lasts seen exception is the cause.", ex))
      .getOrElse(new RuntimeException(s"$msg. Unable to complete in ${arg._1} attempts."))
  }

  def tryNTimesDefaultExceptionFactory(): ((Int, Option[Exception])) => Exception =
    tryNTimesExceptionFactory("Try-N-Times failed")

  def tryNTimesMessage[T](
                           count: Int,
                           message: String,
                           nonFatalExceptions: Set[Class[_ <: Exception]] = Set(classOf[Exception]),
                           waitTimeMs: Long = 1000
                         )(code: => T): T =
    tryNTimes(count, nonFatalExceptions, tryNTimesExceptionFactory(message), waitTimeMs)(code)

  def tryNTimes[T](
                    count: Int,
                    nonFatalExceptions: Set[Class[_ <: Exception]] = Set(classOf[Exception]),
                    exceptionProducer: ((Int, Option[Exception])) => Exception = tryNTimesDefaultExceptionFactory(),
                    waitTimeMs: Long = 1000
                  )(code: => T): T = {
    def isNonFatal(e: Exception): Boolean = {
      nonFatalExceptions.exists(a => a.isAssignableFrom(e.getClass))
    }

    var lastException: Exception = null
    for (i <- 0 to count) {
      try {
        val ret = code
        return ret
      }
      catch {
        case e: Exception if isNonFatal(e) =>
          lastException = e
          if (LOGGER.isDebugEnabled) {
            LOGGER.debug(s"Unable to complete in $i attempt out of $count. Because got: ${e.getClass} : ${e.getMessage}. This is non fatal", e)
          } else {
            LOGGER.info(s"Unable to complete in $i attempt out of $count. Because got: ${e.getClass} : ${e.getMessage}. This is non fatal")
          }
          if (i != count)
            Thread.sleep(waitTimeMs + new Random().nextInt(waitTimeMs.toInt))
        case x: Exception =>
          LOGGER.warn(s"Aborting on $i attempt because of exception", x)
          throw x
      }
    }
    throw exceptionProducer((count, Option(lastException)))
  }

}
