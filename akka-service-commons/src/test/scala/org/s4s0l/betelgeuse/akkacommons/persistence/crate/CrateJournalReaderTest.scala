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

import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Settings
import org.s4s0l.betelgeuse.akkacommons.test.BgTestCrate
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.Await

/**
  * @author Marcin Wielgus
  */
class CrateJournalReaderTest extends
  BgTestCrate {


  private val aService = testWith(new BgPersistenceJournalCrate
    with BgClusteringSharding {
  })

  feature("An utility for querying persistent actors in crate journal") {
    scenario("Can be asked for latest version") {
      new WithService(aService) {
        Given("A new shard storing string values named reader1")
        private val protocol = VersionedEntityActor
          .startSharded[String](Settings("reader1"))(service.clusteringShardingExtension, service.serializationJackson)
        When("We store 2 values in entity 'id1' via SetValue")
        assert(Await.result(protocol.setValue(SetValue("id1", "sth1"))(execContext, self, to * 3), to * 3).isOk)
        assert(Await.result(protocol.setValue(SetValue("id1", "sth2")), to * 3).isOk)

        When("We store 1 value in entity 'id2' via SetValue")
        assert(Await.result(protocol.setValue(SetValue("id2", "sth1")), to * 3).isOk)
        aService.refreshTable("crate_async_write_journal_entity")
        Then("We expect reader to see 2 actors")
        assert(Await.result(aService.service.journalReader.allActorsAsync("reader1"), to).lengthCompare(2) == 0)
      }
    }


  }
}
