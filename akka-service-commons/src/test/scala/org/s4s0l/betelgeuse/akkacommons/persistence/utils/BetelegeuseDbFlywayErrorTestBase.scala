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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.api.FlywayException
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest._
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.interpolation.SQLSyntax

import scala.concurrent.{ExecutionContext, Future}

abstract class BetelegeuseDbFlywayErrorTestBase
  extends TestKit(ActorSystem("TestKit"))
    with FeatureSpecLike
    with BeforeAndAfterEach
    with GivenWhenThen {

  protected val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  var scalike: BetelgeuseDb = _
  lazy val TEST_TABLE_SCHEMA: SQLSyntax = SQLSyntax.createUnsafely(getClassName.toLowerCase)
  val time = new VirtualTime

  feature("BetelgeuseDb migrations correctly handles errors and weird cases") {
    import scalikejdbc._

    scenario("error in syntax should throw exception and be escalated") {
      scalike = new BetelgeuseDb(getConfigForTests("db/migration/flyway_test/fail"))(system.dispatcher, system.scheduler)

      intercept[FlywayException] {
        scalike.setup(Symbol(getClassName))
      }

      refresh(scalike, schema = getClassName.toLowerCase, table = "test_schema_version")
      val installations = scalike.localTx { implicit session =>
        sql"select script, success from test_schema_version".map(it => (it.string(1), it.boolean(2))).list.apply()
      }

      assert(!installations.forall(_._2), "last migration should be failed")
      assert(getLocks(scalike).isEmpty)
    }

    scenario("long running migration should have rolling lock") {
      Given("One process is starting new migration that will take 3 seconds")
      scalike = new BetelgeuseDb(getConfigForTests("db/migration/flyway_test/timeout", lockDuration = "600ms", preLockFinishProlong = "100ms"))(ExecutionContext.Implicits.global, system.scheduler)


      def getCurrentLockTimestamp(db: BetelgeuseDb) = {
        val locks = getLocks(db)
        assert(locks.nonEmpty)
        locks.head._3.toJodaDateTime
      }


      val name = getClassName
      And("Database is set up in separate thread")
      Future {
        scalike.setup(Symbol(name))
      }(system.dispatcher)

      And("We wait until locks are available")
      waitForMigrationLocks(name)

      And("We wait until it starts (200ms until lock rolling)")
      Thread.sleep(50)

      Then("1 lock is placed")
      val lock_0 = getCurrentLockTimestamp(scalike)

      When("lockDuration - preLockFinishProlong finishes (500ms) ")
      Thread.sleep(2000)


      Then("2 lock is placed")
      val lock_1 = getCurrentLockTimestamp(scalike)
      And("2 lock has been prolonged")
      assert(lock_0 isBefore lock_1)

      When("We wait for next lockDuration")
      Thread.sleep(2600)

      Then("next lock is placed")
      val lock_2 = getCurrentLockTimestamp(scalike)
      assert(lock_1 isBefore lock_2)
    }

  }

  private def waitForMigrationLocks(name: String): Unit = {
    AllUtils.tryNTimes("WaitingMigrationLocks", 100, waitTimeMs = 100) {
      val locks = scalike.getLocks(name)
      locks.executor.doInTx {
        implicit session =>
          if (!locks.support.isLocked("FlywayMigration")) {
            throw new Exception()
          }
      }

    }
  }

  def refresh(db: BetelgeuseDb, table: String = "locks", schema: String = "locks"): Unit = {}

  def getLocks(db: BetelgeuseDb, table: String = "locks.locks"): List[(String, String, java.sql.Timestamp)] = db.localTx { implicit session =>
    import scalikejdbc._
    refresh(db)
    val tableName = scalikejdbc.SQLSyntax.createUnsafely(table)
    sql"select name, _version, when_overdue from $tableName".map(it => (it.string(1), it.string(2), it.timestamp(3))).list().apply()
  }

  def getClassName: String = this.getClass.getSimpleName


  def getConfigForTests(location: String, lockDuration: String = "1s", lockAttemptCount: String = "35", lockAttemptInterval: String = "300ms", preLockFinishProlong: String = "300ms"): Config = ConfigFactory.load(s"$getClassName.conf")
    .withFallback(ConfigFactory.parseString(
      s"""db.$getClassName{
         |  migration {
         |    lockDuration=$lockDuration
         |    lockAttemptCount=$lockAttemptCount
         |    lockAttemptInterval=$lockAttemptInterval
         |    preLockFinishProlong=$preLockFinishProlong
         |  }
         |  flyway{
         |    table="test_schema_version"
         |    locations=$location
         |  }
         |}""".stripMargin))
}