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

package org.s4s0l.betelgeuse.akkacommons.test

import org.flywaydb.core.internal.util.StringUtils
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceRoach
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb

/**
  * @author Maciej Flak
  */
@deprecated("Please migrate to BgTestRoach", "?")
trait BgTestWithRoachDb[T <: BgPersistenceRoach] extends BgTestWithPersistence[T]
  with BgTestJackson {

  override def isCleanupOn: Boolean = true

  override def cleanUp(db: BetelgeuseDb): Unit = {
    val schemasString = db.config.getString(s"db.${db.getDefaultPoolName.get}.flyway.schemas")
    val schemas = StringUtils.tokenizeToStringArray(schemasString, ",")
    schemas.foreach { it =>
      DbRoachTest.cleanUp(it)(db)
    }
  }
}
