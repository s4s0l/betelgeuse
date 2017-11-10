package org.s4s0l.betelgeuse.akkacommons.persistence.utils

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