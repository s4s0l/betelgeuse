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

import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializer, SimpleSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.scalatest.FeatureSpec
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class JsonTest extends FeatureSpec with DbRoachTest {

  val serializer: SimpleSerializer = new JacksonJsonSerializer()
  val simpleJsonValue = Map(
    "one" -> "one_value",
    "two" -> Map(
      "two_one" -> "two_one_value",
      "two_two" -> "two_two_value"
    )
  )

  feature("Simple insert and select of a json value") {
    scenario("al lot of data") {
      localTx { implicit session =>
        val serialized: String = serializer.toString(simpleJsonValue)
        sql"insert into json_test (avalue) values ($serialized)".update().apply()
        val (id, jsonString, value) =
          sql"""
               select
                    id, avalue, avalue->'two'->>'two_one'
               from
                    json_test
               where
                    avalue->>'one' = ${simpleJsonValue("one")}
            """.map(it => {
            (
              it.string(1),
              it.string(2),
              it.string(3)
            )
          }).single().apply().get
        assert(value == simpleJsonValue("two").asInstanceOf[Map[String, String]]("two_one"))
        assert(serializer.fromString[Map[String, Any]](jsonString) == simpleJsonValue)
      }

    }
  }

}
