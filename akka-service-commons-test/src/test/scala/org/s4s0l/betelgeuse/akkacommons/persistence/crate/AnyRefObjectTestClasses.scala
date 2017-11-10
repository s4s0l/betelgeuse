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

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.{CrateDbObjectMapper, _}
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.Wrapper
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseEntityObject
import org.s4s0l.betelgeuse.akkacommons.serialization.DepricatedTypeWithMigrationInfo
import scalikejdbc.WrappedResultSet

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */


case class AnyRefTable1(i: Int, o: AnyRefObject)

object AnyRefTable1 extends BetelgeuseEntityObject[AnyRefTable1] {
  override def apply(m: scalikejdbc.ResultName[AnyRefTable1])(rs: WrappedResultSet): AnyRefTable1 = {
    new AnyRefTable1(
      rs.int(m.i),
      rs.get[AnyRefObject](m.o)
    )
  }
}

case class AnyRefTable2(i: Int, o: AnyRef)

object AnyRefTable2 extends BetelgeuseEntityObject[AnyRefTable2] {
  override def apply(m: scalikejdbc.ResultName[AnyRefTable2])(rs: WrappedResultSet): AnyRefTable2 = {
    new AnyRefTable2(
      rs.int(m.i),
      rs.get[AnyRefObject](m.o).objectValue
    )
  }
}

case class SampleObject(s: String) extends CrateDbObject

object SampleObject extends CrateDbObjectMapper[SampleObject] {

  override def ctag: ClassTag[SampleObject] = classTag[SampleObject]

  override def toSql(no: SampleObject): Map[String, Wrapper] = {
    Map(
      "s" -> no.s
    )
  }

  override def fromSql(resolver: Internals.ObjectAttributeResolver): SampleObject = {
    new SampleObject(
      resolver.string("s").get
    )
  }

}


case class SampleDeprecatedObject(s: String) extends DepricatedTypeWithMigrationInfo with CrateDbObject {
  override def convertToMigratedType(): AnyRef = SampleObject(s)
}

object SampleDeprecatedObject extends CrateDbObjectMapper[SampleDeprecatedObject] {

  override def ctag: ClassTag[SampleDeprecatedObject] = classTag[SampleDeprecatedObject]

  override def toSql(no: SampleDeprecatedObject): Map[String, Wrapper] = {
    Map(
      "s" -> no.s
    )
  }

  override def fromSql(resolver: Internals.ObjectAttributeResolver): SampleDeprecatedObject = {
    new SampleDeprecatedObject(
      resolver.string("s").get
    )
  }

}