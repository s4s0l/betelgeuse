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

import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSettings
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.scalatest.{FeatureSpec, GivenWhenThen, Outcome}
import scalikejdbc._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Maciej Flak
  */
class RoachDbLocksTest extends FeatureSpec
  with GivenWhenThen
  with DbRoachTest {

  val dbLocks = new RoachDbLocks()

  implicit val ec: ExecutionContext = ExecutionContext.global


  feature("The user can perform locking operations in roach database") {

    info("As an user i want locks in roach db to work based on optimistic locking")
    scenario("Full lifecycle") {
      Given("Two processes try to initialize locking mechanism")
      And("One of them is called 'dbLocks'")
      And("The other is 'other'")
      val other = new RoachDbLocks()

      When("Both start in the same time")

      val future = Future {
        dbLocks.initLocks(sqlExecutionTxSessionFactory)
      }

      Then("The first process finishes with no error")
      sqlExecution(implicit session => {
        dbLocks.initLocks(sqlExecutionTxSessionFactory)
      })
      And("Second process finishes with no error")
      Await.ready(future, 1 minute)
      sqlExecution(implicit session => {
        And("Lock table is present")
        assert(dbLocks.isLocksTablePresent)
        And("'LOCK' is not locked")
        assert(!dbLocks.isLocked("LOCK"))
        And("'LOCK' is not belonging to dbLocks")
        assert(!dbLocks.isLockOurs("LOCK"))
        And("'LOCK' has no locking party")
        assert(dbLocks.getLockingParty("LOCK").isEmpty)
      })
      When("dbLocks lock 'LOCK'")
      dbLocks.lock("LOCK", sqlExecutionTxSessionFactory)
      sqlExecution(implicit session => {
        Then("dbLocks sees 'LOCK' as locked")
        assert(dbLocks.isLocked("LOCK"))
        And("dbLocks sees 'LOCK' as owned by it")
        assert(dbLocks.isLockOurs("LOCK"))
        And("dbLocks sees 'LOCK's locking party is dbLock")
        assert(dbLocks.getLockingParty("LOCK").contains((dbLocks.uuid, dbLocks.humanReadableName)))


        And("other sees 'LOCK' as locked")
        assert(other.isLocked("LOCK"))
        And("other does not claim it has 'LOCK'")
        assert(!other.isLockOurs("LOCK"))
        And("other sees 'LOCK's locking party is dbLock")
        assert(other.getLockingParty("LOCK").contains((dbLocks.uuid, dbLocks.humanReadableName)))
      })

      When("other locks LOCK2")
      other.lock("LOCK2", sqlExecutionTxSessionFactory)
      sqlExecution(implicit session => {
        Then("other sees LOCK2 as locked by itself")
        assert(other.isLocked("LOCK2"))
        assert(other.isLockOurs("LOCK2"))
        assert(other.getLockingParty("LOCK2").contains((other.uuid, other.humanReadableName)))

        And("dbLocks sees 'LOCK2' locked by other")
        assert(dbLocks.isLocked("LOCK2"))
        assert(!dbLocks.isLockOurs("LOCK2"))
        assert(dbLocks.getLockingParty("LOCK2").contains((other.uuid, other.humanReadableName)))
      })

      When("other locks LOCK2 while it is locked by itself")
      other.lock("LOCK2", sqlExecutionTxSessionFactory)
      Then("lock succeeds")

      When("other tries to lock 'LOCK'")
      Then("Exception is raised")
      assertThrows[RuntimeException](other.lock("LOCK", sqlExecutionTxSessionFactory, DbLocksSettings(lockAttemptCount = 2)))

      sqlExecution(implicit session => {
        When("other unlocks 'LOCK'")
        Then("No exception thrown")
        other.unlock("LOCK")
        And("Lock is not released")
        assert(dbLocks.isLockOurs("LOCK"))


        When("other unlocks 'LOCK2'")
        Then("No Exception")
        other.unlock("LOCK2")
        And("'LOCK2' is released")
        assert(!dbLocks.isLocked("LOCK2"))
        assert(!dbLocks.isLockOurs("LOCK2"))
        assert(dbLocks.getLockingParty("LOCK2").isEmpty)

        When("other unlocks 'LOCK2' again when not locked")
        Then("No Exception")
        other.unlock("LOCK2")
        And("'LOCK2' is released")
        assert(!dbLocks.isLocked("LOCK2"))
      })


      When("dbLock performs block code in lock 'LOCK'")
      var x = false
      dbLocks.runLocked("LOCK", sqlExecutionTxSessionFactory) { _ =>
        x = true
      }
      Then("Code block is performed")
      assert(x)
      sqlExecution(implicit session => {
        And("'LOCK' is released afterwards")
        assert(!dbLocks.isLocked("LOCK"))
        assert(!dbLocks.isLockOurs("LOCK"))
        assert(dbLocks.getLockingParty("LOCK").isEmpty)
      })
    }


    scenario("Locking is really locking") {
      val other = new RoachDbLocks()
      dbLocks.initLocks(sqlExecutionTxSessionFactory)
      @volatile
      var execOneRunning: Boolean = false
      @volatile
      var execTwoRunning: Boolean = false

      val future1: Future[String] = Future {
        sqlExecution { implicit session =>
          dbLocks.runLocked("lock", sqlExecutionTxSessionFactory, DbLocksSettings()) { implicit session =>
            try {
              execOneRunning = true
              assert(execOneRunning != execTwoRunning)
              Thread.sleep(1000)
            } finally {
              execOneRunning = false
            }
            "ok"
          }
        }
      }

      val future2 = Future {
        sqlExecution { implicit session =>
          other.runLocked("lock", sqlExecutionTxSessionFactory, DbLocksSettings()) { implicit session =>
            try {
              execTwoRunning = true
              assert(execOneRunning != execTwoRunning)
              Thread.sleep(1000)
            } finally {
              execTwoRunning = false
            }
            "ok"
          }
        }
      }

      assert(Await.result(future1, 5 second) == "ok")
      assert(Await.result(future2, 5 second) == "ok")

    }
  }

  override def cleanUp(cfg: Config)(implicit session: DBSession): Unit = {
    if (dbLocks.isLocksTablePresent) {
      //      dbLocks.deleteAllLocks
      dbLocks.dropLocksTable
    }
  }

  override protected def withFixture(test: NoArgTest): Outcome = {
    sqlExecution { implicit session =>
      if (dbLocks.isLocksTablePresent) {
        //        dbLocks.deleteAllLocks
        dbLocks.dropLocksTable
      }
    }
    super.withFixture(test)
  }
}
