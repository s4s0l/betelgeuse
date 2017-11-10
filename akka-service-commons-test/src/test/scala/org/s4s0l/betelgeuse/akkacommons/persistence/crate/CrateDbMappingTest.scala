/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.util
import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports._
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalatest._
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class CrateDbMappingTest extends FeatureSpec with DbCrateTest {

  private lazy val mt = MappingTable.syntax("mt")
  private lazy val column = MappingTable.column

  private def refresh(implicit session: DBSession): Unit = refreshTable("mappping_table")


  feature("BetelgeuseDbs make it easy to use scalike") {
    scenario("Basic sql dsl syntax check") {
      sqlExecution { implicit session =>
        withSQL {
          insert.into(MappingTable).namedValues(
            column.i -> 1,
            column.s -> "value")
        }.update.apply()

        refresh

        val ret = withSQL {
          select
            .from(MappingTable.as(mt))
            .where.eq(mt.s, "value")
        }
          .map(a => (a.int(mt.resultName.i), a.string(mt.resultName.s)))
          .list.apply()
        assert(ret == List((1, "value")))
      }
    }

    scenario("Basic sql syntax check") {
      sqlExecution { implicit session =>

        val (i, s) = (2, "value2")
        sql"""insert into ${MappingTable.table} (${MappingTable.column.i},${MappingTable.column.s}) values ($i, $s)""".update.apply()

        refresh

        val ret = sql"""select ${mt.result.*} from ${MappingTable.as(mt)} where ${mt.s} = $s""".map(MappingTable(mt.resultName)).list.apply()

        assert(ret == List(MappingTable(2, "value2", null, null, null, null, null, null, null)))

        val (i2, s2, a2) = (3, "value3", Array("a", "b"))
        sql"""insert into ${MappingTable.table} (
                  ${MappingTable.column.i},
                  ${MappingTable.column.s},
                  ${MappingTable.column.a}
              ) values (
                  $i2,
                  $s2,
                  ${param(a2)}
              )""".update.apply()

        refresh

        val ret2 = sql"""select ${mt.result.*} from ${MappingTable.as(mt)} where ${mt.i} = $i2""".map(MappingTable(mt.resultName)).list.apply()
        assert(ret2.map(x => util.Arrays.asList(x.a: _*)) == List(util.Arrays.asList(a2: _*)))

      }
    }

  }

  feature("BetelgeuseDB for crate allows for storing nested objects as objects in crate") {
    scenario("Sql nesting check") {
      sqlExecution { implicit session =>
        val o4 = new NestedObject(11, "11", new O(new Date), Array("1", "1"), List("a", "x"), List(new OA(10, "10"), new OA(20, "20")), null, null)
        val (i4, s4, a4, oa4) = (4, "value4", Array("a", "b"), Array(new OA(1, "1"), new OA(2, "2")))
        val (la, loa, ms, moa) = (List("x", "y"), List(new OA(1, "x1"), OA(2, "x2")), Map("key" -> "value"), Map("oa1" -> new OA(9, "9")))
        sql"""insert into ${MappingTable.table} (
                  ${MappingTable.column.i},
                  ${MappingTable.column.s},
                  ${MappingTable.column.a},
                  ${MappingTable.column.nested_object},
                  ${MappingTable.column.oa},
                  ${MappingTable.column.la},
                  ${MappingTable.column.loa},
                  ${MappingTable.column.ms},
                  ${MappingTable.column.moa}
              ) values (
                  $i4,
                  $s4,
                  ${param(a4)},
                  ${param(o4)},
                  ${param(oa4)},
                  ${param(la)},
                  ${param(loa)},
                  ${param(ms)},
                  ${param(moa)}
              )""".update.apply()


        refresh

        val ret = sql"""select ${mt.result.*} from ${MappingTable.as(mt)} where ${mt.s} = $s4""".map(MappingTable(mt.resultName)).list.apply()

        assert(ret.head.i == 4)
        assert(ret.head.s == "value4")
        assert(ret.head.a.toList == List("a", "b"))
        assert(ret.head.nested_object.i == 11)
        assert(ret.head.nested_object.s == "11")
        assert(ret.head.nested_object.o == o4.o)
        assert(ret.head.nested_object.a.toList == List("1", "1"))
        assert(ret.head.nested_object.la == List("a", "x"))
        assert(ret.head.nested_object.loa == List(new OA(10, "10"), new OA(20, "20")))
        assert(ret.head.oa.toList == oa4.toList)
        assert(ret.head.la == la)
        assert(ret.head.loa == loa)
        assert(ret.head.ms == ms)
        assert(ret.head.moa == moa)
      }
    }
  }

}
