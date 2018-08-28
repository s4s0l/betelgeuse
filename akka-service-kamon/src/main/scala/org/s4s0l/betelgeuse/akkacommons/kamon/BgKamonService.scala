/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.kamon

import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.spm.SPMReporter
import kamon.system.SystemMetrics
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.kamon.LoggingReporter._

/**
  * @author Marcin Wielgus
  */
trait BgKamonService extends BgService {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgKamonService])

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with kamon.conf with fallback to...")
    ConfigFactory.parseResources("kamon.conf").withFallback(super.customizeConfiguration)
  }


  protected def createMetricSelectors: Seq[Selector] = {
    Seq(
      GroupByPrefixAndTag("jvm.memory.buffer-pool.", Set("pool"), SuffixExtractor("jvm.memory.buffer-pool.", Seq("measure"))),
      GroupByNamesInAndTag("jvm.memory", Set("segment"), TagExtractor(Seq("measure"))),
      GroupByNamesInAndTag("jvm.gc", Set(), TagExtractor(Seq("collector"))),
      GroupByNamesInAndTag("jvm.gc.promotion", Set(), TagExtractor(Seq("space"))),
      GroupByNamesInAndTag("host.memory", Set(), TagExtractor(Seq("mode"))),
      GroupByPrefixAndTag("akka.system.", Set(), SuffixExtractor("akka.system.", Seq("tracked"))),
      GroupByNamesInAndTag("executor.values", Set("executor.pool", "executor.tasks"), Set("name"), SuffixExtractor("executor.", Seq("state", "setting"))),
      GroupByNamesInAndTag("executor.stats", Set("executor.threads", "executor.queue"), Set("name"), SuffixExtractor("executor.", Seq("state"))),
      GroupByPrefixAndTag("akka.actor.", Set("path"), SuffixExtractor("akka.actor.", Seq())),
      GroupByPrefixAndTag("akka.group.", Set("group"), SuffixExtractor("akka.group.", Seq())),
      GroupByPrefixAndTag("akka.cluster.sharding.region.", Set("type"), SuffixExtractor("akka.cluster.sharding.region.", Seq())),
      GroupByPrefixAndTag("akka.cluster.sharding.shard.", Set("type"), SuffixExtractor("akka.cluster.sharding.shard.", Seq())),

    )
  }

  override protected def initialize(): Unit = {
    val kamonEnabled = config.getBoolean("kamon.enabled")
    if (kamonEnabled) {
      LOGGER.info("Kamon will be enabled")
      Kamon.reconfigure(config)
      SystemMetrics.startCollecting()
      Kamon.loadReportersFromConfig()

      if (config.hasPath("kamon.spm.token") && config.getString("kamon.spm.token").nonEmpty) {
        LOGGER.info("SPM token found, enabling Kamon spm reporter")
        Kamon.addReporter(new SPMReporter())
      } else {
        LOGGER.info("SPM Kamon reporter disabled as no kamon.spm.token provided")
      }
      LOGGER.info("Kamon enabled.")
    } else {
      LOGGER.info("Kamon disabled.")
    }
    super.initialize()
    if (kamonEnabled) {
      Kamon.addReporter(new LoggingReporter(Logging(system, "Kamon"), createMetricSelectors))
    }
  }

  override protected def beforeShutdown(): Unit = {
    try {
      Kamon.stopAllReporters()
    } finally {
      super.beforeShutdown()
    }
  }
}
