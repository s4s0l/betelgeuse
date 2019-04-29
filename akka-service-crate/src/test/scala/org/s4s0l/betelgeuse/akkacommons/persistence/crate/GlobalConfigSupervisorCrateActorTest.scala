/*
 * Copyright© 2019 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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


package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import akka.pattern._
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BgClusteringPubSub
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor.ConfigurationChangedAck
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.{BgPersistenceJournalCrate, CrateJournalReader}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestCrate
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class GlobalConfigSupervisorCrateActorTest extends BgTestCrate {

  private val testedService = testWith(new BgPersistenceJournalCrate with BgClusteringPubSub {

  })

  feature("Global config mechanism for distributing changes in configuration, and fast access to it") {
    scenario("Starts global config stores sth in it and it is available on reboot") {
      initialRun

      restartServices()
      new WithService(testedService) {
        this.refreshTable("crate_async_write_journal_entity")
      }
      secondRun
    }
  }

  private def initialRun = {
    new WithService(testedService) {
      private val mediator = service.clusteringPubSubExtension.asPubSubWithDefaultMediator
      private val access = service.persistenceExtension.dbAccess

      private val coordinator = GlobalConfigsFactory.createGlobalConfig[String, String]("testConfig",
        new CrateJournalReader(access),
        mediator)
      coordinator.awaitInit()
      assert(Await.result(coordinator.apply("avalue"), 10 seconds).isEmpty)

      GlobalConfigsFactory.eventPublisher(mediator, "testConfig").emitChange("avalue", "THE VALUE").pipeTo(self)
      GlobalConfigsFactory.eventPublisher(mediator, "testConfig").emitChange("avalue2", "THE VALE2").pipeTo(self)
      testKit.expectMsgType[ConfigurationChangedAck](20 seconds)
      assert(Await.result(coordinator.apply("avalue"), 10 seconds).isDefined)
    }
  }

  private def secondRun = {
    new WithService(testedService) {
      private val mediator = service.clusteringPubSubExtension.asPubSubWithDefaultMediator
      private val access = service.persistenceExtension.dbAccess

      private val coordinator = GlobalConfigsFactory.createGlobalConfig[String, String]("testConfig",
        new CrateJournalReader(access),
        mediator)
      coordinator.awaitInit()

      assert(Await.result(coordinator.apply("avalue"), 10 seconds).get == "THE VALUE")
    }
  }
}


