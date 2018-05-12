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
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSettings.DbLocksSingle
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSupport.TxExecutor
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession

import scala.concurrent.ExecutionContext

/**
  * @author Marcin Wielgus
  */
trait DbLocksSupport {

  def isLocked(lockName: String)(implicit session: DBSession): Boolean

  def initLocks(txExecutor: TxExecutor): Unit

  def runLocked[T](lockName: String, txExecutor: TxExecutor, settings: DbLocksSettings = DbLocksSingle())
                  (code: DBSession => T)(implicit ex: ExecutionContext, scheduler: Scheduler): T
}

object DbLocksSupport {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  def noOpLocker: DbLocksSupport = new DbLocksSupport {

    override def initLocks(txExecutor: TxExecutor): Unit = {
      LOGGER.warn("You are using NoOpLocker. This is very dangerous for consistency!")
    }


    override def isLocked(lockName: String)(implicit session: DBSession): Boolean = false

    override def runLocked[T](lockName: String, txExecutor: TxExecutor, settings: DbLocksSettings = DbLocksSingle())
                             (code: DBSession => T)(implicit ex: ExecutionContext, scheduler: Scheduler): T = {
      throw new Exception("You are using NoOpLocker. This is very dangerous for consistency!")
    }
  }

  trait TxExecutor {
    def doInTx[T](code: DBSession => T): T
  }
}

class NoOpLocker(config: Config) extends DbLocksSupport {
  private val LOGGER = LoggerFactory.getLogger(getClass)


  override def isLocked(lockName: String)(implicit session: DBSession): Boolean = false

  override def initLocks(txExecutor: TxExecutor): Unit = {
    LOGGER.warn("You are using NoOpLocker. This is very dangerous for consistency!")
  }

  override def runLocked[T](lockName: String, txExecutor: TxExecutor, settings: DbLocksSettings = DbLocksSingle())
                           (code: DBSession => T)(implicit ex: ExecutionContext, scheduler: Scheduler): T = {
    throw new Exception("You are using NoOpLocker. This is very dangerous for consistency!")
  }

}