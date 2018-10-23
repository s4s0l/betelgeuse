/*
 * Copyright© 2018 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import akka.actor.Scheduler
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSettings.DbLocksSingle
import scalikejdbc.DBSession

import scala.concurrent.ExecutionContext

/**
  * @author Marcin Wielgus
  */
class DbLocks(val support: DbLocksSupport, val executor: DbLocksSupport.TxExecutor) {

  def runLocked[T](lockName: String, settings: DbLocksSettings = DbLocksSingle())
                  (code: DBSession => T)(implicit ex: ExecutionContext, scheduler: Scheduler): T = {
    support.runLocked(lockName, executor, settings)(code)
  }
}
