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

import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateDbMappingTest._
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.CrateDbObjectMapper
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.FeatureSpec
/**
  * @author Marcin Wielgus
  */
class CrateDbObjectIntrospectorTest extends FeatureSpec {

  feature("All-utils finding companion objects is usable for crate db objct mappers") {

    scenario("Lookup companion object implementation from object") {
      val theObject = new O(new Date())
      assert(AllUtils.findCompanionForObject[O, CrateDbObjectMapper[O]](theObject) == O)
    }

    scenario("Lookup companion object implementation from class") {
      assert(AllUtils.findCompanionForClass[CrateDbObjectMapper[O]](classOf[O]) == O)
    }

    scenario("Lookup companion object should not fail when companion does not implement given class") {
      assert(AllUtils.findCompanionForClassOption[CrateDbObjectMapper[MappingTable]](classOf[MappingTable]).isEmpty)
    }

    scenario("Lookup companion object should not fail when companion does not have companion object") {
      assert(AllUtils.findCompanionForClassOption[CrateDbObjectMapper[CrateDbObjectIntrospectorTest]](classOf[CrateDbObjectIntrospectorTest]).isEmpty)
    }

  }


}
