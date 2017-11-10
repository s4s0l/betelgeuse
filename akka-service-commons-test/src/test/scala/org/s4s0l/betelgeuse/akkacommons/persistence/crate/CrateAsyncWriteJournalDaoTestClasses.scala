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

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports._
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.Wrapper
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */


case class RegularEvent(s: String, i: Int, seq: Seq[String])

case class JsonEvent(s: String, i: Int, seq: Seq[String]) extends JacksonJsonSerializable

case class CrateEvent(s: String, i: Int, seq: Seq[String]) extends CrateDbObject

object CrateEvent extends CrateDbObjectMapper[CrateEvent] {
  override def ctag: ClassTag[CrateEvent] = classTag[CrateEvent]

  override def toSql(no: CrateEvent): Map[String, Wrapper] = {
    Map[String, Wrapper](
      "s" -> no.s,
      "i" -> no.i,
      "seq" -> no.seq.toList,
    )
  }

  override def fromSql(resolver: Internals.ObjectAttributeResolver): CrateEvent = {
    new CrateEvent(
      resolver.string("s").get,
      resolver.int("i").get,
      resolver.get[List[String]]("seq").get,
    )
  }

}