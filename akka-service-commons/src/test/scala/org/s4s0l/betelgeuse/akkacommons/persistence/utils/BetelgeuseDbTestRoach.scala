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
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FeatureSpec}
import scalikejdbc.interpolation.SQLSyntax

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._


/**
  * @author Marcin Wielgus
  */
class BetelgeuseDbTestRoach extends FeatureSpec with BeforeAndAfterAll {

  private val akkaConfig: Config = ConfigFactory.load("BetelgeuseDbTestRoach.conf")
  lazy val scalike = new BetelgeuseDb(akkaConfig)(concurrent.ExecutionContext.Implicits.global, (new VirtualTime).scheduler)
  lazy val TEST_TABLE_SCHEMA: SQLSyntax = SQLSyntax.createUnsafely("betelgeusedbtestroach")

  feature("BetelgeuseDb allows use of scalikeJdbc") {
    import scalikejdbc._
    import scalikejdbc.streams._
    scenario("Streaming works") {
      implicit val system = ActorSystem("stream_sample_system", akkaConfig)
      implicit val actorMaterializer = ActorMaterializer()
      val dbAccess = new DbAccessImpl(scalike, scalike.getDefaultPoolName.get)
      val stream = dbAccess.stream {
        sql"select 1".map(rs => rs.int(1)).iterator()
      }
      val res = Await.result(stream.runWith(Sink.seq), 15.seconds)
      assert(res == Seq(1))
    }

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
      val tablesInSchema = scalike.readOnly { implicit session =>
        sql"""show tables from $TEST_TABLE_SCHEMA"""
          .map(_.string(1)).list.apply()
      }

      assert(tablesInSchema.size == 2) //test_table + versioning table
    }
  }

  override protected def beforeAll(): Unit = {
    scalike.setupAll()

  }

  override protected def afterAll(): Unit = {
    import scalikejdbc._
    scalike.localTx { implicit session =>
      sql"drop table $TEST_TABLE_SCHEMA.test_schema_version".execute().apply()
      sql"drop table $TEST_TABLE_SCHEMA.test_table".execute().apply()
    }
    scalike.closeAll()
  }
}
