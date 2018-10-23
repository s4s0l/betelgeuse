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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKit
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSettings.DbLocksSingle
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest.dropDatabase
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.{FeatureSpecLike, GivenWhenThen, Outcome}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Maciej Flak
  */
class RoachDbLocksTest extends TestKit(ActorSystem("a"))
  with FeatureSpecLike
  with GivenWhenThen
  with DbRoachTest {

  val dbLocks = new RoachDbLocks("roach_db_locks_test", "locks_table")

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler


  feature("The user can perform locking operations in roach database") {

    scenario("Full lifecycle") {
      Given("Two processes try to initialize locking mechanism")
      And("One of them is called 'dbLocks'")
      And("The other is 'other'")
      val other = new RoachDbLocks("roach_db_locks_test", "locks_table")

      When("Both start in the same time")

      val future = Future {
        other.initLocks(localTxExecutor)
      }
      //      Thread.sleep(10)
      Then("The first process finishes with no error")
      dbLocks.initLocks(localTxExecutor)
      println("LOCKS INITIALIZED!")
      And("Second process finishes with no error")
      Await.ready(future, 1 minute)
      localTx(implicit session => {
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
      dbLocks.lock("LOCK", localTxExecutor)
      localTx(implicit session => {
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
      other.lock("LOCK2", localTxExecutor)
      localTx(implicit session => {
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
      other.lock("LOCK2", localTxExecutor)
      Then("lock succeeds")

      When("other tries to lock 'LOCK'")
      Then("Exception is raised")
      assertThrows[RuntimeException](other.lock("LOCK", localTxExecutor, DbLocksSingle(lockAttemptCount = 2)))

      localTx(implicit session => {
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
      dbLocks.runLocked("LOCK", localTxExecutor) { _ =>
        x = true
      }
      Then("Code block is performed")
      assert(x)
      localTx(implicit session => {
        And("'LOCK' is released afterwards")
        assert(!dbLocks.isLocked("LOCK"))
        assert(!dbLocks.isLockOurs("LOCK"))
        assert(dbLocks.getLockingParty("LOCK").isEmpty)
      })
    }


    scenario("Locking is really locking") {
      val other = new RoachDbLocks("roach_db_locks_test", "locks_table")
      dbLocks.initLocks(localTxExecutor)
      @volatile
      var execOneRunning: Boolean = false
      @volatile
      var execTwoRunning: Boolean = false

      val future1: Future[String] = Future {
        localTx { implicit session =>
          dbLocks.runLocked("lock", localTxExecutor, DbLocksSingle()) { implicit session =>
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
        localTx { implicit session =>
          other.runLocked("lock", localTxExecutor, DbLocksSingle()) { implicit session =>
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

  feature("Delete me please") {

    (1 to 2).foreach { i =>
      scenario(s"deleting creating $i") {
        val other = new RoachDbLocks("roach_db_locks_test", "locks_table")
        val future1 = Future {
          other.initLocks(localTxExecutor)
        }
        val future2 = Future {
          dbLocks.initLocks(localTxExecutor)
        }
        dbLocks.initLocks(localTxExecutor)
        Await.ready(future1, 1 minute)
        Await.ready(future2, 1 minute)
        localTx { implicit session =>
          assert(dbLocks.isLocksTablePresent)
          dbLocks.dropLocksTable
        }
        AllUtils.tryNTimes("Delete me locks", 10) {
          localTxExecutor.doInTx { implicit session =>
            DbRoachTest.dropDatabase("roach_db_locks_test")
          }
        }
      }
    }
  }

  override def cleanUp(db: BetelgeuseDb): Unit = {
    AllUtils.tryNTimes("RoachDbLocksTestDropLocksTestDb", 2) {
      db.localTx { implicit session =>
        dropDatabase("roach_db_locks_test")(session)
      }
    }
    super.cleanUp(db)
  }

  override protected def withFixture(test: NoArgTest): Outcome = {
    AllUtils.tryNTimes("RoachDbLocksTestDropLocksTestDb", 2) {
      db.localTx { implicit session =>
        dropDatabase("roach_db_locks_test")(session)
      }
    }
    super.withFixture(test)
  }
}
