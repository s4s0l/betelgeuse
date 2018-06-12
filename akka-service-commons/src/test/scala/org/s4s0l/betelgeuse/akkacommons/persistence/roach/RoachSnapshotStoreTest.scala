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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import java.lang.System.getProperty

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory.parseResources
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.s4s0l.betelgeuse.utils.AllUtils._

/**
  * @author Marcin Wielgus
  */
class RoachSnapshotStoreTest extends SnapshotStoreSpec(
  config =
    parseResources("persistence-snapstore-roach.conf")
      .withFallback(parseResources("persistence-journal-roach.conf"))
      .withFallback(placeholderResourceConfig("persistence-datasource-roach.conf-template",
        Map(
          "datasource" -> "RoachSnapshotStoreTest".toLowerCase,
          "schemaname" -> "RoachSnapshotStoreTest".toLowerCase,
          "address" -> getProperty("roach.db.hostTestPayload", "127.0.0.1"),
          "port" -> getProperty("roach.db.port", "26257")
        )))
      .withFallback(parseResources("persistence-roach.conf"))
      .withFallback(placeholderResourceConfig("persistence-datasource.conf-template",
        Map(
          "datasource" -> "RoachSnapshotStoreTest".toLowerCase,
          "locations" -> "db/migration/BgPersistenceSnapStoreRoach",
          "schema" -> "RoachSnapshotStoreTest".toLowerCase
        )
      ))
      .withFallback(parseResources("persistence.conf"))
      .withFallback(ConfigFactory.parseString(
        s"""
           |persistence-snapstore-roach.serializerHintsClass="${classOf[RoachTestHints].getName}"
        """.stripMargin))
) {

  protected override def beforeAll(): Unit

  = {
    DbRoachTest.cleanEverything(config)
    system.registerExtension(BgPersistenceExtension)
    super.beforeAll()
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
  }
}
