package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.param
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalatest.FeatureSpec
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class AnyRefObjectTest extends FeatureSpec with DbCrateTest {

  feature("Classes that implement DepricatedTypeWithMigrationInfo are converted to new version automatically"){

    scenario("reading deprecated object"){
      sqlExecution { implicit session =>
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

    scenario("Any ref object can be sucessfully saved and restored from database") {
      sqlExecution { implicit session =>
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


    scenario("Any ref object can be sucessfully saved and restored from database, no explicit any ref object") {
      sqlExecution { implicit session =>
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
