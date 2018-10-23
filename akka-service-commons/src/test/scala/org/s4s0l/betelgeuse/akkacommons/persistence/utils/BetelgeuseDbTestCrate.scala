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

import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.ConfigFactory
import scalikejdbc.interpolation.SQLSyntax

/**
  * @author Maciej Flak
  */
class BetelgeuseDbTestCrate extends BetelgeuseDbTestRoach {
  override lazy val scalike = new BetelgeuseDb(ConfigFactory.load("BetelgeuseDbTestCrate.conf"))(concurrent.ExecutionContext.Implicits.global, (new VirtualTime).scheduler)
  override lazy val TEST_TABLE_SCHEMA: SQLSyntax = SQLSyntax.createUnsafely("betelgeusedbtestcrate")
}
