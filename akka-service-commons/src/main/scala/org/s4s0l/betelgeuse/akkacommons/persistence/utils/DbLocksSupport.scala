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

import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession

/**
  * @author Marcin Wielgus
  */
trait DbLocksSupport {

  def initLocks(implicit dBSession: DBSession)

  def runLocked(lockName: String, lockSettings: DbLocksSettings = DbLocksSettings())(code: => Unit)(implicit dBSession: DBSession): Unit
}

object DbLocksSupport {
  def noopLocker:DbLocksSupport = new DbLocksSupport {

    override def initLocks(implicit dBSession: DBSession): Unit = {}

    override def runLocked(lockName: String, lockSettings: DbLocksSettings = DbLocksSettings())(code: => Unit)(implicit dBSession: DBSession): Unit = code
  }
}

class NoopLocker(config: Config) extends DbLocksSupport {
  private val LOGGER = LoggerFactory.getLogger(getClass)

  override def initLocks(implicit dBSession: DBSession): Unit = {}

  override def runLocked(lockName: String, lockSettings: DbLocksSettings = DbLocksSettings())(code: => Unit)(implicit dBSession: DBSession): Unit = {
    LOGGER.warn("You are using NoopLocker. This is very dangerous for consistency!")
    code
  }

}