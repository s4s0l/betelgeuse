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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.s4s0l.betelgeuse.akkacommons.BgServiceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.test.BgTestCrate
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class BgPersistenceCrateTest extends BgTestCrate {
  private val aService = testWith(new BgPersistenceCrate {})


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

  feature("Provides working locking mechanizm") {
    scenario("Locking and unlcking") {
      new WithService(aService) {
        private val extension = BgPersistenceExtension.apply(system)
        When("Asked for lock")
        extension.dbAccess.update { implicit session =>
          extension.dbAccess.locksSupport().asInstanceOf[CrateDbLocks].lock("SOME_LOCK_123")
        }
        Then("Lock holds")
        extension.dbAccess.update { implicit session =>
          assert(extension.dbAccess.locksSupport().asInstanceOf[CrateDbLocks].isLockOurs("SOME_LOCK_123"))
        }
        When("Asked to release")
        extension.dbAccess.update { implicit session =>
          extension.dbAccess.locksSupport().asInstanceOf[CrateDbLocks].unlock("SOME_LOCK_123")
        }
        Then("Lock is released")
        extension.dbAccess.update { implicit session =>
          assert(!extension.dbAccess.locksSupport().asInstanceOf[CrateDbLocks].isLockOurs("SOME_LOCK_123"))
        }
      }
    }
  }

}
