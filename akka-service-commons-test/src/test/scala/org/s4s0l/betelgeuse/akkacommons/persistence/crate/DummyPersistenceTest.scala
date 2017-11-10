package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.util.UUID

import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalatest.FeatureSpec
import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
class DummyPersistenceTest extends FeatureSpec with DbCrateTest {

  def insert(recs: Int)(implicit session: DBSession): Unit = {
    val batchParams = (1 to recs).map { _ =>
      val s = UUID.randomUUID().toString
      Seq(s, s)
    }
    sql"insert into dummy (k,s) values (?, ?)".batch(batchParams: _*).apply()
  }

  feature("Insert loads of data") {
    scenario("al lot of data") {
      val tests = 1
      val counts = 1
      sqlExecution { implicit session =>
        (1 to tests).foreach { _=>

        insert(10 * counts)
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        insert(10 * counts)
        refreshTable("dummy")
        Thread.sleep(1000)
        refreshTable("dummy")
        refreshTable("dummy")
        insert(1 * counts)
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")
        refreshTable("dummy")

        }
      }
    }
  }

}
