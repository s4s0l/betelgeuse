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

import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals._
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseEntityObject
import scalikejdbc._

import scala.reflect.ClassTag

case class MappingTable(
                         i: Int,
                         s: String,
                         nested_object: NestedObject,
                         a: Array[String],
                         oa: Array[OA],
                         la: List[String],
                         loa: List[OA],
                         ms: Map[String, String],
                         moa: Map[String, OA]
                       )


import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports._

object MappingTable extends BetelgeuseEntityObject[MappingTable] {

  override def tableName = "mappping_table"

  def apply(m: ResultName[MappingTable])(rs: WrappedResultSet): MappingTable = {
    new MappingTable(
      rs.int(m.i),
      rs.string(m.s),
      rs.get[NestedObject](m.nested_object),
      rs.get[Array[String]](m.a),
      rs.get[Array[OA]](m.oa),
      rs.get[List[String]](m.la),
      rs.get[List[OA]](m.loa),
      rs.get[Map[String, String]](m.ms),
      rs.get[Map[String, OA]](m.moa)
    )
  }

}

case class NestedObject(i: Int, s: String, o: O, a: Array[String],
                        la: List[String],
                        loa: List[OA],
                        ms: Map[String, String],
                        moa: Map[String, OA])

object NestedObject extends CrateDbObjectMapper[NestedObject] {

  import CrateScalikeJdbcImports._

  override val ctag: ClassTag[NestedObject] = classTag[NestedObject]

  def toSql(no: NestedObject): Map[String, CrateSqlishValueHolder] = {
    Map[String, CrateSqlishValueHolder](
      "i" -> no.i,
      "s" -> no.s,
      "o" -> no.o,
      "a" -> no.a,
      "la" -> no.la,
      "loa" -> no.loa,
      "ms" -> no.ms,
      "moa" -> no.moa
    )
  }

  def fromSql(x: ObjectAttributeResolver): NestedObject = {


    new NestedObject(
      x.int("i").getOrElse(0),
      x.string("s").orNull,
      x.get[O]("o").orNull,
      x.get[Array[String]]("a").orNull,
      x.get[List[String]]("la").orNull,
      x.get[List[OA]]("loa").orNull,
      x.get[Map[String, String]]("ms").orNull,
      x.get[Map[String, OA]]("moa").orNull
    )
  }
}

case class O(date: Date)

object O extends CrateDbObjectMapper[O] {

  override def ctag: ClassTag[O] = classTag[O]

  def toSql(o: O): Map[String, Wrapper] = {
    Map[String, Wrapper](
      "date" -> o.date
    )
  }

  def fromSql(x: ObjectAttributeResolver): O = {
    new O(
      x.date("date").orNull
    )
  }
}

case class OA(i: Int, s: String)

object OA extends CrateDbObjectMapper[OA] {

  override def ctag: ClassTag[OA] = classTag[OA]

  def toSql(no: OA): Map[String, CrateSqlishValueHolder] = {
    Map[String, CrateSqlishValueHolder](
      "i" -> no.i,
      "s" -> no.s,
    )
  }

  def fromSql(x: ObjectAttributeResolver): OA = {
    new OA(
      x.int("i").getOrElse(0),
      x.string("s").orNull
    )
  }
}


