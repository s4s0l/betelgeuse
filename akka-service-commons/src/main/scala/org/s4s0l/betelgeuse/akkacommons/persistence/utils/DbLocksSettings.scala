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

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
sealed trait DbLocksSettings {
  def duration: Duration

  def lockAttemptCount: Int

  def lockAttemptInterval: Duration
}

object DbLocksSettings {

  case class DbLocksSingle(
                            maxDuration: Duration = 60 seconds,
                            lockAttemptCount: Int = 15,
                            lockAttemptInterval: Duration = 2 second
                          ) extends DbLocksSettings {
    override def duration: Duration = maxDuration
  }

  case class DbLocksRolling(
                             duration: FiniteDuration = 5 seconds,
                             lockAttemptCount: Int = 15,
                             lockAttemptInterval: Duration = 2 second,
                             schedulerLockShuffle: FiniteDuration = 500 millis
                           ) extends DbLocksSettings
}