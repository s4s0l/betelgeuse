/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 14:45
 */

package org.s4s0l.betelgeuse.akkacommons.test

import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.BetelgeuseAkkaPersistence
import scalikejdbc.DBSession

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaTestWithPersistence[T <: BetelgeuseAkkaPersistence] extends BetelgeuseAkkaServiceSpecLike[T] {

  def isCleanupOn: Boolean = false

  def cleanUp(dbName: String, configUsed:Config)(implicit session: DBSession): Unit = {

  }

  def prepareDatabase(): Unit = {
    import scala.collection.JavaConverters._
    val config = service.config
    val dbs = config.getConfig("db").root.keySet.asScala.toList
    dbs.foreach { it =>
      DbTest.runWithoutSchemaMigration(config, it,{(cfg,sess) => cleanUp(it, cfg)(sess)})
    }
  }

  override def initializeService(): Unit = {
    if (isCleanupOn)
      prepareDatabase()
    super.initializeService()
  }
}
