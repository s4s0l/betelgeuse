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

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.AnyRefObjectTest._
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.{CrateDbObject, CrateDbObjectMapper, classTag, param, _}
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.Wrapper
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseEntityObject
import org.s4s0l.betelgeuse.akkacommons.serialization.DepricatedTypeWithMigrationInfo
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalatest.FeatureSpec
import scalikejdbc.{WrappedResultSet, _}

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */
class AnyRefObjectTest extends FeatureSpec with DbCrateTest {

  feature("Classes that implement DeprecatedTypeWithMigrationInfo are converted to new version automatically"){

    scenario("reading deprecated object"){
      localTx { implicit session =>
        val sampleObject1 = new SampleDeprecatedObject("sample1")
        sql"""insert into ${AnyRefTable1.table} (
                  ${AnyRefTable1.column.i},
                  ${AnyRefTable1.column.o}
              ) values (
                  9,
                  ${param(AnyRefObject(sampleObject1))}
              )""".update.apply()

        refreshTable(AnyRefTable1.tableName)
        val mt = AnyRefTable1.syntax("mt")
        val ret1 = sql"select ${mt.result.*} from ${AnyRefTable1.as(mt)} where ${mt.i}=9"
          .map(AnyRefTable1(mt.resultName))
          .first()
          .apply()

        assert(ret1.get == new AnyRefTable1(9, AnyRefObject(classOf[SampleDeprecatedObject].getName,SampleObject("sample1"))))

      }
    }

  }


  feature("Top table scalike objects in crate can have no knowledge about objects it contains") {

    scenario("Any ref object can be successfully saved and restored from database") {
      localTx { implicit session =>
        val sampleObject1 = new SampleObject("sample1")
        sql"""insert into ${AnyRefTable1.table} (
                  ${AnyRefTable1.column.i},
                  ${AnyRefTable1.column.o}
              ) values (
                  3,
                  ${param(AnyRefObject(sampleObject1))}
              )""".update.apply()

        refreshTable(AnyRefTable1.tableName)
        val mt = AnyRefTable1.syntax("mt")
        val ret1 = sql"select ${mt.result.*} from ${AnyRefTable1.as(mt)} where ${mt.i}=3"
          .map(AnyRefTable1(mt.resultName))
          .first()
          .apply()

        assert(ret1.get == new AnyRefTable1(3, AnyRefObject(sampleObject1)))

      }
    }


    scenario("Any ref object can be successfully saved and restored from database, no explicit any ref object") {
      localTx { implicit session =>
        val sampleObject1 = new SampleObject("sample2")
        sql"""insert into ${AnyRefTable2.table} (
                  ${AnyRefTable1.column.i},
                  ${AnyRefTable1.column.o}
              ) values (
                  6,
                  ${param(AnyRefObject(sampleObject1))}
              )""".update.apply()

        refreshTable(AnyRefTable2.tableName)
        val mt = AnyRefTable2.syntax("mt")
        val ret1 = sql"select ${mt.result.*} from ${AnyRefTable2.as(mt)}"
          .map(AnyRefTable2(mt.resultName))
          .first()
          .apply()

        assert(ret1.get == new AnyRefTable2(6, new SampleObject("sample2")))

      }
    }
  }
}

object AnyRefObjectTest {

  case class AnyRefTable1(i: Int, o: AnyRefObject)

  case class AnyRefTable2(i: Int, o: AnyRef)

  case class SampleObject(s: String) extends CrateDbObject

  case class SampleDeprecatedObject(s: String) extends DepricatedTypeWithMigrationInfo with CrateDbObject {
    override def convertToMigratedType(): AnyRef = SampleObject(s)
  }

  object AnyRefTable1 extends BetelgeuseEntityObject[AnyRefTable1] {
    override def apply(m: scalikejdbc.ResultName[AnyRefTable1])(rs: WrappedResultSet): AnyRefTable1 = {
      new AnyRefTable1(
        rs.int(m.i),
        rs.get[AnyRefObject](m.o)
      )
    }
  }

  object AnyRefTable2 extends BetelgeuseEntityObject[AnyRefTable2] {
    override def apply(m: scalikejdbc.ResultName[AnyRefTable2])(rs: WrappedResultSet): AnyRefTable2 = {
      new AnyRefTable2(
        rs.int(m.i),
        rs.get[AnyRefObject](m.o).objectValue
      )
    }
  }

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

}