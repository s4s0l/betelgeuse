/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 04:05
 *
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BetelgeuseAkkaClusteringPubSub
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor.ConfigurationChangedAck
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BetelgeuseAkkaPersistenceJournalCrate
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaTestWithCrateDb
import akka.pattern._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class GlobalConfigSupervisorActorTest extends BetelgeuseAkkaTestWithCrateDb[BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringPubSub] {
  //  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def createService(): BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringPubSub
  = new BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringPubSub {

  }

  feature("Global config mechnizm for distributing changes in configuration, and fast access to it") {
    scenario("Starts global config stores sth in it and it is available on reboot") {
      initialRun

      restartService()
      refreshTable("crate_async_write_journal_entity")

      secondRun
    }
  }

  private def initialRun = {
    implicit val to: Timeout = defaultTimeout
    val mediator = service.clusteringPubSubExtension.asPubSubWithDefaultMediator
    val access = service.persistenceExtension.dbAccess

    val coordinator = GlobalConfigsFactory.createGlobalConfig[String, String]("testConfig",
      new GlobalConfigCrateQueryFacade(access),
      mediator)
    coordinator.awaitInit()
    assert(Await.result(coordinator.apply("avalue"), 10 seconds).isEmpty)

    GlobalConfigsFactory.eventPublisher(mediator, "testConfig").emitChange("avalue", "THE VALUE").pipeTo(self)
    GlobalConfigsFactory.eventPublisher(mediator, "testConfig").emitChange("avalue2", "THE VALE2").pipeTo(self)
    testKit.expectMsgType[ConfigurationChangedAck](20 seconds)
    assert(Await.result(coordinator.apply("avalue"), 10 seconds).isDefined)

  }

  private def secondRun = {
    implicit val to: Timeout = defaultTimeout
    val mediator = service.clusteringPubSubExtension.asPubSubWithDefaultMediator
    val access = service.persistenceExtension.dbAccess

    val coordinator = GlobalConfigsFactory.createGlobalConfig[String, String]("testConfig",
      new GlobalConfigCrateQueryFacade(access),
      mediator)
    coordinator.awaitInit()

    assert(Await.result(coordinator.apply("avalue"), 10 seconds).get == "THE VALUE")
  }
}
