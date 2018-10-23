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

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.{CrateDbObject, CrateDbObjectMapper}
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.{ObjectAttributeResolver, Wrapper}
import org.s4s0l.betelgeuse.akkacommons.serialization.DepricatedTypeWithMigrationInfo
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.reflect.{ClassTag, classTag}

/**
  * @author Marcin Wielgus
  */
case class AnyRefObject(objectType: String, objectValue: CrateDbObject)

object AnyRefObject extends CrateDbObjectMapper[AnyRefObject] {

  def apply(objectType: String, objectValue: CrateDbObject): AnyRefObject = new AnyRefObject(objectType, objectValue)

  def apply(objectValue: CrateDbObject): AnyRefObject = new AnyRefObject(objectValue.getClass.getName, objectValue)

  override def ctag: ClassTag[AnyRefObject] = classTag[AnyRefObject]

  def toSql(o: AnyRefObject): Map[String, Wrapper] = {
    import CrateScalikeJdbcImports._
    val mapper: CrateDbObjectMapper[AnyRef] = o.objectValue.crateDbObjectMapper()
    val converter: Internals.SqlConverter[AnyRef] = mapper.sqlConverter

    Map[String, Wrapper](
      "objectType" -> o.objectType,
      "objectValue" -> converter.toSqlType(o.objectValue)
    )
  }

  def fromSql(x: ObjectAttributeResolver): AnyRefObject = {
    val objectType = x.string("objectType").get
    val mapper: CrateDbObjectMapper[_] = getMapper(objectType)
    new AnyRefObject(
      objectType,
      mapper.sqlConverter.fromSqlType(x.map("objectValue")).get.asInstanceOf[AnyRef] match {
        case dep: DepricatedTypeWithMigrationInfo =>
          dep.convertToMigratedType().asInstanceOf[CrateDbObject]
        case something => something.asInstanceOf[CrateDbObject]
      }
    )
  }

  private def getMapper(objectType: String) = {
    val theClass = Class.forName(objectType)
    val mapper = AllUtils.findCompanionForClass[CrateDbObjectMapper[AnyRef]](theClass)
    mapper
  }
}
