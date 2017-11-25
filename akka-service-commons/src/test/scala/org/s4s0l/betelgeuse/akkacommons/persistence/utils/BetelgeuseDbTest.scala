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



package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FeatureSpec}

/**
  * @author Marcin Wielgus
  */
class BetelgeuseDbTest extends FeatureSpec with BeforeAndAfterAll {

  lazy val scalike = new BetelgeuseDb(ConfigFactory.load("BetelgeuseDbTest.conf"))

  feature("BetelgeuseDb allows use of dcalikeJdbc") {
    import scalikejdbc._

    scenario("Readonly interpolation queries") {
      val memberIds = scalike.readOnly { implicit session =>
        sql"select 13".map(_.long(1)).list.apply()
      }

      assert(memberIds == List(13))
    }
    scenario("tx interpolation queries") {
      val vals = scalike.localTx { implicit session =>
        sql"select val from test_table".map(_.string(1)).list.apply()
      }
      assert(vals == List("1"))
    }
    scenario("Direct usage of scalike api") {
      val tablesInSchema = scalike.underlyingPureScalikeJdbcDb().readOnly { implicit session =>
        sql"""SELECT TABLE_NAME FROM information_schema.tables
           WHERE TABLE_NAME = 'test_schema_version' AND table_schema= 'betelgeusedbtest'"""
          .map(_.string(1)).list.apply()
      }

      assert(tablesInSchema.size == 1)
    }
  }

  override protected def beforeAll(): Unit = {
    scalike.setupAll()

  }

  override protected def afterAll(): Unit = {
    import scalikejdbc._
    scalike.localTx { implicit session =>
      sql"delete from locks.locks".execute().apply()
      sql"drop table betelgeusedbtest.test_schema_version".execute().apply()
      sql"drop table betelgeusedbtest.test_table".execute().apply()
    }
    scalike.closeAll()
  }
}
