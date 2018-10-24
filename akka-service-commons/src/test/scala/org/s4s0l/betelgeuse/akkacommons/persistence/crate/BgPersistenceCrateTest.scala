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
import org.s4s0l.betelgeuse.akkacommons.BgServiceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.test.BgTestCrate
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import scalikejdbc._

import scala.concurrent.ExecutionContext

/**
  * @author Marcin Wielgus
  */
class BgPersistenceCrateTest extends BgTestCrate {
  private val aService = testWith(new BgPersistenceCrate {})

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val scheduler: Scheduler = (new VirtualTime).scheduler


  feature("Default configuration is created") {
    scenario("When no parameters are specified") {
      new WithService(aService) {

        When("Using persistence Extension")
        private val extension = BgPersistenceExtension.apply(system)
        Then("Default pool name is system name")
        assert(extension.defaultPoolName == "BgPersistenceCrateTest")
        assert(extension.defaultPoolName == BgServiceExtension.get(system).serviceInfo.id.systemName)
        Then("Flyway migration is performed")
        private val x = extension.dbAccess.query { implicit session =>
          sql"select test_value from test_table".map(_.string(1)).first().apply()
        }
        assert(x.get == "value")
        And("Tables are in proper schema")
        private val expectedSchema = BgServiceExtension(system).serviceInfo.id.systemName.toLowerCase
        assert(extension.defaultSchemaName == expectedSchema)
        assert(extension.dbAccess.query { implicit session =>
          sql"select table_schema from information_schema.tables where table_schema=$expectedSchema and table_name='test_table'".map(_.string(1)).first().apply()
        }.get == expectedSchema)
      }
    }
  }

  feature("Provides working locking mechanism") {
    scenario("Locking and unlocking") {
      new WithService(aService) {
        private val extension = BgPersistenceExtension.apply(system)
        When("Asked for lock")
        extension.dbAccess.locksSupport().runLocked("SOME_LOCK_123") { implicit session =>
          Then("Lock holds")
          extension.dbAccess.locksSupport().support.asInstanceOf[CrateDbLocks].isLockOurs("SOME_LOCK_123")
          When("Asked to release")
        }
        Then("Lock is released")
        extension.dbAccess.update { implicit session =>
          assert(!extension.dbAccess.locksSupport().support.asInstanceOf[CrateDbLocks].isLockOurs("SOME_LOCK_123"))
        }
      }
    }
  }

}
