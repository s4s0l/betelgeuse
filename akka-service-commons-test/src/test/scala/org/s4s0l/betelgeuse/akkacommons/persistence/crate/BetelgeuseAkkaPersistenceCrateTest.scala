/*
 * Copyright© 2017 the original author or authors.
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

import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaServiceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.BetelgeuseAkkaPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaTestWithCrateDb
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaPersistenceCrateTest extends BetelgeuseAkkaTestWithCrateDb[BetelgeuseAkkaPersistenceCrate] {
  override def createService(): BetelgeuseAkkaPersistenceCrate = new BetelgeuseAkkaPersistenceCrate {}


  feature("Default configuration is created") {
    scenario("When no parameters are specified") {
      When("Using persistence Extension")
      val extension = BetelgeuseAkkaPersistenceExtension.apply(system)
      Then("Default pool name is system name")
      assert(extension.defaultPoolName == "BetelgeuseAkkaPersistenceCrateTest")
      assert(extension.defaultPoolName == BetelgeuseAkkaServiceExtension.get(system).serviceInfo.id.systemName)
      Then("Flyway migration is performed")
      val x = extension.query { implicit session =>
        sql"select test_value from test_table".map(_.string(1)).first().apply()
      }
      assert(x.get == "value")
      And("Tables are in proper schema")
      val expectedSchema = BetelgeuseAkkaServiceExtension(system).serviceInfo.id.systemName.toLowerCase
      assert(extension.defaultSchemaName == expectedSchema)
      assert(extension.query { implicit session =>
        sql"select table_schema from information_schema.tables where table_schema=$expectedSchema and table_name='test_table'".map(_.string(1)).first().apply()
      }.get == expectedSchema)
    }
  }

  feature("Provides working locking mechanizm") {
    scenario("Locking and unlcking") {
      val extension = BetelgeuseAkkaPersistenceExtension.apply(system)
      When("Asked for lock")
      extension.update { implicit session =>
        extension.locksSupport().asInstanceOf[CrateDbLocks].lock("SOME_LOCK_123")
      }
      Then("Lock holds")
      extension.update { implicit session =>
        assert(extension.locksSupport().asInstanceOf[CrateDbLocks].isLockOurs("SOME_LOCK_123"))
      }
      When("Asked to release")
      extension.update { implicit session =>
        extension.locksSupport().asInstanceOf[CrateDbLocks].unlock("SOME_LOCK_123")
      }
      Then("Lock is released")
      extension.update { implicit session =>
        assert(!extension.locksSupport().asInstanceOf[CrateDbLocks].isLockOurs("SOME_LOCK_123"))
      }
    }
  }

}
