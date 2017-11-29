/*
 * Copyright© 2017 the original author or authors.
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

import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistence
import scalikejdbc.DBSession

/**
  * @author Marcin Wielgus
  */
trait BgTestWithPersistence[T <: BgPersistence] extends BgServiceSpecLike[T] {

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
