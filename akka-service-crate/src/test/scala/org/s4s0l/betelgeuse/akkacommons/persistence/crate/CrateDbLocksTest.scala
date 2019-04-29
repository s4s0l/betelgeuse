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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.actor.Scheduler
import com.miguno.akka.testing.VirtualTime
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbLocksSettings.DbLocksSingle
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalatest.{FeatureSpec, GivenWhenThen}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class CrateDbLocksTest extends FeatureSpec
  with GivenWhenThen
  with DbCrateTest {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val scheduler: Scheduler = (new VirtualTime).scheduler

  val dbLocks = new CrateDbLocks()


  feature("The user can perform locking operations in crate database") {

    scenario("Full lifecycle") {
      info("As an user i want locks in crate db to work based on optimistic locking")
      Given("Two processes try to initialize locking mechanism")
      And("One of them is called 'dbLocks'")
      And("The other is 'other'")

      val other = new CrateDbLocks()

      When("Both start in the same time")

      val future = Future {
        dbLocks.initLocks(localTxExecutor)
      }

      Then("The first process finishes with no error")
      localTx(implicit session => {
        dbLocks.initLocks(localTxExecutor)
      })
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

        When("dbLocks lock 'LOCK'")
        dbLocks.lock("LOCK", localTxExecutor)

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


        When("other locks LOCK2")
        other.lock("LOCK2", localTxExecutor)

        Then("other sees LOCK2 as locked by itself")
        assert(other.isLocked("LOCK2"))
        assert(other.isLockOurs("LOCK2"))
        assert(other.getLockingParty("LOCK2").contains((other.uuid, other.humanReadableName)))

        And("dbLocks sees 'LOCK2' locked by other")
        assert(dbLocks.isLocked("LOCK2"))
        assert(!dbLocks.isLockOurs("LOCK2"))
        assert(dbLocks.getLockingParty("LOCK2").contains((other.uuid, other.humanReadableName)))


        When("other locks LOCK2 while it is locked by itself")
        other.lock("LOCK2", localTxExecutor)
        Then("lock succeeds")

        When("other tries to lock 'LOCK'")
        Then("Exception is raised")
        assertThrows[RuntimeException](other.lock("LOCK", localTxExecutor, DbLocksSingle(lockAttemptCount = 2)))


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


        When("dbLock performs block code in lock 'LOCK'")
        var x = false
        dbLocks.runLocked("LOCK", localTxExecutor) { _ =>
          x = true
        }
        Then("Code block is performed")
        assert(x)

        And("'LOCK' is released afterwards")
        assert(!dbLocks.isLocked("LOCK"))
        assert(!dbLocks.isLockOurs("LOCK"))
        assert(dbLocks.getLockingParty("LOCK").isEmpty)
      })
    }
  }


  override def cleanUp(db: BetelgeuseDb): Unit = {
    db.localTx { implicit session =>
      if (dbLocks.isLocksTablePresent) {
        dbLocks.deleteAllLocks
        dbLocks.dropLocksTable
      }
    }
    super.cleanUp(db)
  }
}
