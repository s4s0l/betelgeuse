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

import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.s4s0l.betelgeuse.utils.UuidUtils
import org.scalatest.FeatureSpec
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class DummyPersistenceTest extends FeatureSpec with DbCrateTest {

  def insert(recs: Int)(implicit session: DBSession): Unit = {
    val batchParams = (1 to recs).map { _ =>
      val s = UuidUtils.timeBasedUuid().toString
      Seq(s, s)
    }
    sql"insert into dummy (k,s) values (?, ?)".batch(batchParams: _*).apply()
  }

  feature("Insert loads of data") {
    scenario("al lot of data") {
      val tests = 1
      val counts = 1
      localTx { implicit session =>
        (1 to tests).foreach { _=>

        insert(10 * counts)
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        insert(10 * counts)
        refreshTable("dummy")
        Thread.sleep(1000)
        refreshTable("dummy")
        refreshTable("dummy")
        insert(1 * counts)
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")

        }
      }
    }
  }

}
