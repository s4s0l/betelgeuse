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

package org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity

import akka.pattern._
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Settings
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BgPersistenceJournalCrate
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class VersionedEntityActorTest extends
  BgTestService {


  private val aService = testWith(new BgPersistenceJournalCrate
    with BgClusteringSharding {
  })
  aService.to = 5 seconds

  aService.timeout = 5 seconds

  feature("An utility actor is persistent and versioned, supports optimistic locking") {
    scenario("Can be queried for current version") {
      new WithService(aService) {

        Given("A new shard storing string values named test1")
        private val protocol = VersionedEntityActor.startSharded[String](Settings("test1"))(service.clusteringShardingExtension)
        When("Getting version for non existing entity id1")
        private val getValueVersion = GetValueVersion("id1")
        protocol.getVersion(getValueVersion).pipeTo(self)
        Then("Version returned should have value == 0")
        testKit.expectMsg(to, ValueVersionResult(getValueVersion.messageId, VersionedId("id1", 0)))

        When("We store value 'sth' in entity 'id1' via SetValue")
        private val setValue1 = SetValue("id1", "sth")
        protocol.setValue(setValue1).pipeTo(self)
        Then("We expect it to return confirmation that new version is 1")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueOk(setValue1.messageId, VersionedId("id1", 1)))

        When("We ask for a version again")
        private val getValueVersion2 = GetValueVersion("id1")
        protocol.getVersion(getValueVersion2).pipeTo(self)
        Then("Version returned should now have value == 1")
        testKit.expectMsg(to, ValueVersionResult(getValueVersion2.messageId, VersionedId("id1", 1)))

      }
    }


    scenario("Getting values") {
      new WithService(aService) {

        Given("A new shard storing string values named test3")
        private val protocol = VersionedEntityActor.startSharded[String](Settings("test3"))(service.clusteringShardingExtension)
        Given("entity 'id1' has value 'sth' in version 2")
        private val value1 = SetValue("id1", "sth1")
        protocol.setValue(value1).pipeTo(self)
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueOk(value1.messageId, VersionedId("id1", 1)))
        private val value2 = SetValue("id1", "sth2")
        protocol.setValue(value2).pipeTo(self)
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueOk(value2.messageId, VersionedId("id1", 2)))


        When("We ask for version 1")
        protocol.getValue(GetValue(VersionedId("id1", 1))).pipeTo(self)
        Then("We expect it to return a value 'sth1'")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.ValueOk(VersionedId("id1", 1), "sth1"))

        When("We ask for version 2")
        protocol.getValue(GetValue(VersionedId("id1", 2))).pipeTo(self)
        Then("We expect it to return a value 'sth2'")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.ValueOk(VersionedId("id1", 2), "sth2"))

        When("We ask for version 3")
        protocol.getValue(GetValue(VersionedId("id1", 3))).pipeTo(self)
        Then("We expect it to return a None in version 3")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.ValueNotOk(VersionedId("id1", 3), ValueMissingException(VersionedId("id1", 3))))
      }
    }


    scenario("Optimistic locking") {
      new WithService(aService) {

        Given("A new shard storing string values named test2")
        private val protocol = VersionedEntityActor.startSharded[String](Settings("test2"))(service.clusteringShardingExtension)
        Given("entity 'id1' has value 'sth' in version 2")
        private val setValue1 = SetValue("id1", "sth")
        protocol.setValue(setValue1).pipeTo(self)
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueOk(setValue1.messageId, VersionedId("id1", 1)))
        private val setValue2 = SetValue("id1", "sth")
        protocol.setValue(setValue2).pipeTo(self)
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueOk(setValue2.messageId, VersionedId("id1", 2)))


        When("We try to update its value with version 2")
        private val setValue3 = SetVersionedValue(VersionedId("id1", 2), "somethingNew")
        protocol.setVersionedValue(setValue3).pipeTo(self)

        Then("The result is failure with version 2")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueNotOk(setValue3.messageId, ValueUpdateOptimisticException(VersionedId("id1", 2))))


        When("We update with version 4")
        private val setValue4 = SetVersionedValue(VersionedId("id1", 4), "somethingNew")
        protocol.setVersionedValue(setValue4).pipeTo(self)
        Then("The result is still failure with version 2")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueNotOk(setValue4.messageId, ValueUpdateOptimisticException(VersionedId("id1", 2))))

        When("We update with version 3")
        private val setValue5 = SetVersionedValue(VersionedId("id1", 3), "somethingNew")
        protocol.setVersionedValue(setValue5).pipeTo(self)
        Then("The result is success with version 3")
        testKit.expectMsg(to, VersionedEntityActor.Protocol.SetValueOk(setValue5.messageId, VersionedId("id1", 3)))

      }

    }


  }
}