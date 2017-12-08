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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}

import com.typesafe.config.Config
import io.crate.shade.org.postgresql.util.PSQLException
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.{DbLocksSettings, DbLocksSupport}
import org.s4s0l.betelgeuse.akkacommons.utils.DnsUtils
import org.s4s0l.betelgeuse.utils.AllUtils
import org.s4s0l.betelgeuse.utils.AllUtils._
import org.slf4j.LoggerFactory
import scalikejdbc.interpolation.SQLSyntax
import scalikejdbc.{DBSession, _}

import scala.collection.immutable

/**
  * @author Marcin Wielgus
  */
class RoachDbLocks(val schema: String = "locks", locksTable: String = "locks")
  extends DbLocksSupport {




  def this(config: Config) = {
    this(AllUtils.toConfigOptionApi(config).string("roach.locks.schema").getOrElse("locks"),
      AllUtils.toConfigOptionApi(config).string("roach.locks.table").getOrElse("locks"))
  }

  private val LOGGER = LoggerFactory.getLogger(getClass)

  val uuid: String = UUID.randomUUID().toString

  val humanReadableName: String = DnsUtils.getCurrentNodeHostName

  private val unsafeSchema = SQLSyntax.createUnsafely(schema)

  private val unsafeLocksTable = SQLSyntax.createUnsafely(locksTable)

  override def initLocks(implicit session: DBSession): Unit = {
    tryNTimes(5, Set(classOf[PSQLException]),
      tryNTimesExceptionFactory(s"Lock mechanizm initiation failed. Holder $uuid")) {
      ensureLocksTableExists
    }
  }




  def isLocked(lockName: String)(implicit session: DBSession): Boolean = {
    getLockingParty(lockName).isDefined
  }

  def isLockOurs(lockName: String)(implicit session: DBSession): Boolean = {
    getLockingParty(lockName).exists(_._1 == uuid)
  }


  def lock(lockName: String, lockSettings: DbLocksSettings = DbLocksSettings())(implicit session: DBSession): Date = {
    tryNTimes(lockSettings.lockAttemptCount,
      Set(classOf[Exception]),
      tryNTimesExceptionFactory(s"Taking lock failed. Holder $uuid"),
      (lockSettings.lockAttemptInterval / 2).toMillis) {
      lockAttempt(lockName, lockSettings)(session)
    }
  }

  private def lockAttempt(lockName: String, lockSettings: DbLocksSettings)(implicit session: DBSession): Date = {
    val now = new Timestamp(new Date().getTime)
    val willBeOverdue = {
      val current = Calendar.getInstance()
      current.add(Calendar.MILLISECOND, lockSettings.maxDuration.toMillis.toInt)
      new Timestamp(current.getTime.getTime)
    }

    sql"""SELECT when_overdue,by_who_id, "_version" FROM $unsafeSchema.$unsafeLocksTable
         WHERE  name = $lockName
       """.map(x => (x.timestamp(1), x.string(2), x.long(3))).first().apply() match {
      case None =>
        sql"""INSERT INTO  $unsafeSchema.$unsafeLocksTable
                (name,
                when_locked ,
                when_overdue,
                by_who ,
                by_who_id )
         VALUES ($lockName, $now, $willBeOverdue, $humanReadableName, $uuid)
       """.update().apply()
        LOGGER.debug(s"Lock $lockName inserted. Holder $uuid.")
      case Some((when_overdue, _, version))
        if when_overdue.getTime < now.getTime =>
        val updated =
          sql"""UPDATE $unsafeSchema.$unsafeLocksTable
             SET when_overdue = $willBeOverdue,
                 when_locked = $now,
                 by_who = $humanReadableName,
                 by_who_id = $uuid
             WHERE
                name = $lockName AND "_version" = $version
           """.update().apply()
        if (updated != 1) {
          throw new Exception(s"Optimistic lock exception")
        }
        LOGGER.debug(s"Lock $lockName updated as was overdue. Holder $uuid.")
      case Some((_, by_who_id, version))
        if by_who_id == uuid =>
        val updated =
          sql"""UPDATE $unsafeSchema.$unsafeLocksTable
             SET when_overdue = $willBeOverdue,
                 by_who = $humanReadableName,
                 by_who_id = $uuid
             WHERE
                name = $lockName

           """.executeUpdate().apply()
        if (updated != 1) {
          throw new Exception(s"Optimistic lock exception")
        }
        LOGGER.debug(s"Lock $lockName prolonged. Holder $uuid.")
      case Some(a) =>
        throw new Exception(s"Lock taken by ${a._2} will expire at ${a._1}")
    }
    new Date(willBeOverdue.getTime)
  }


  def unlock(lockName: String)(implicit session: DBSession): Unit = {

    val now = new Timestamp(new Date().getTime)

    def deleteCmd(version: Long): Int = {
      val count =
        sql"""DELETE FROM $unsafeSchema.$unsafeLocksTable
          WHERE name=$lockName AND "_version"=$version""".executeUpdate().apply()

      if (count != 0) {
        LOGGER.debug(s"Lock $lockName released,actually there was $count locks. Holder $uuid.")
      }
      count
    }

    sql"""SELECT by_who_id,when_overdue, _version from $unsafeSchema.$unsafeLocksTable
          WHERE NAME=$lockName
       """.map(r => (r.string(1), r.timestamp(2), r.long(3))).first().apply() match {
      case None =>
        LOGGER.warn(s"Requested lock release, but no lock was found for lockName = $lockName! Holder $uuid.")
      case Some((_, whneOverdue, version))
        if whneOverdue.getTime < now.getTime =>
        if (deleteCmd(version) != 1) {
          LOGGER.warn(s"Tried to delete overdue lock for $lockName, but was not found, or modified by someone in the process. Holder $uuid.")
        }
      case Some((by_who, _, version))
        if by_who == uuid =>
        if (deleteCmd(version) != 1) {
          LOGGER.warn(s"Tried to delete my lock for $lockName, but was not found, or modified by someone in the process. Holder $uuid.")
        }
      case Some((by_who, _, _)) =>
        LOGGER.warn(s"Tried to delete my lock for $lockName, lock is owned by $by_who. Holder $uuid.")
    }

  }

  override def runLocked(lockName: String, lockSettings: DbLocksSettings = DbLocksSettings())(code: => Unit)(implicit session: DBSession): Unit = {
    try {
      lock(lockName, lockSettings)
      code
    } finally {
      unlock(lockName)
    }
  }

  /**
    *
    *
    * @return (UUID, name)return
    */
  def getLockingParty(lockName: String)(implicit session: DBSession): Option[(String, String)] = {
    val now = new Timestamp(new Date().getTime)
    sql"""
         SELECT by_who_id, by_who, when_overdue FROM $unsafeSchema.$unsafeLocksTable
          WHERE name=$lockName
       """
      .map(r => (r.string(1), r.string(2), r.timestamp(3)))
      .first().apply()
      .filter(_._3.getTime > now.getTime)
      .map(x => (x._1, x._2))
  }


  private def ensureLocksTableExists(implicit dbSession: DBSession): Boolean = {
    val exists: Boolean = isLocksTablePresent
    if (!exists) {
      dbSession.execute(s"CREATE DATABASE IF NOT EXISTS locks")
      dbSession.execute(
        s"""create table $schema.$locksTable(
                name string PRIMARY KEY,
                when_locked TIMESTAMP WITH TIME ZONE not null,
                when_overdue TIMESTAMP WITH TIME ZONE not null,
                by_who string,
                _version INT DEFAULT unique_rowid(),
                by_who_id string not null)""")
    }
    true
  }

  def deleteAllLocks(implicit dbSession: DBSession): Boolean = {
    dbSession.execute(s"delete from $schema.$locksTable")
  }

  def dropLocksTable(implicit dbSession: DBSession): Boolean = {
    dbSession.execute(s"drop table $schema.$locksTable")
    true
  }

  def isLocksTablePresent(implicit dbSession: DBSession): Boolean = {
    val foundTables: immutable.Seq[String] =
      sql"""SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_NAME = $locksTable AND table_schema= $schema"""
        .map(_.string(1)).list.apply()
    val exists = foundTables.size == 1
    exists
  }
}
