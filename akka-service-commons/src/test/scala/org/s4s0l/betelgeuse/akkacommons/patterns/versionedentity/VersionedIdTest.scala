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

package org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity

import org.scalatest.FunSuite

/**
  * @author Marcin Wielgus
  */
class VersionedIdTest extends FunSuite {

  test("to and from string") {
    assert(VersionedId("id@5").version == 5)
    assert(VersionedId("id@5").id == "id")
    assert(VersionedId("id", 5).toString == "id@5")
  }
}
