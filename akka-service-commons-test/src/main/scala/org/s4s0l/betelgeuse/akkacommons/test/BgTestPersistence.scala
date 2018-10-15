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

package org.s4s0l.betelgeuse.akkacommons.test

import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceRoach
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseDb

/**
  * @author Marcin Wielgus
  */
trait BgTestPersistence extends BgTestService {


  override def initializeServices(): Unit = {
    if (isCleanupOn)
      prepareDatabase()
    super.initializeServices()
  }

  def isCleanupOn: Boolean = false

  def prepareDatabase(): Unit = {
    servicesUnderTest.filter(_.isInstanceOf[BgPersistenceRoach]).foreach { service =>
      import scala.collection.JavaConverters._
      val config = service.service.asInstanceOf[BgService].config
      val dbs = config.getConfig("db").root.keySet.asScala.toList
      dbs.foreach { it =>
        DbTest.runWithoutSchemaMigration(config, it, cleanUp)
      }
    }

  }

  def cleanUp(db: BetelgeuseDb): Unit = {
  }

}
