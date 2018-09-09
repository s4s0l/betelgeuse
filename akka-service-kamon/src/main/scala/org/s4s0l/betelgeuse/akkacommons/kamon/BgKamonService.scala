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
package org.s4s0l.betelgeuse.akkacommons.kamon

import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
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
      GroupByNamesInAndTag("executor.values", Set("executor.pool", "executor.tasks"), Set("type", "name"), SuffixExtractor("executor.", Seq("state", "setting"))),
      GroupByNamesInAndTag("executor.stats", Set("executor.threads", "executor.queue"), Set("name"), SuffixExtractor("executor.", Seq("state"))),
      GroupByPrefixAndTag("akka.actor.", Set("path"), SuffixExtractor("akka.actor.", Seq())),
      GroupByPrefixAndTag("akka.group.", Set("group"), SuffixExtractor("akka.group.", Seq())),
      GroupByPrefixAndTag("akka.cluster.sharding.region.", Set("type"), SuffixExtractor("akka.cluster.sharding.region.", Seq())),
      GroupByPrefixAndTag("akka.cluster.sharding.shard.", Set("type"), SuffixExtractor("akka.cluster.sharding.shard.", Seq())),
      GroupByPrefixAndTag("akka.remote.", Set("direction"), SuffixExtractor("akka.remote.", Seq())),
      GroupByNamesInAndTag("process.ulimit", Set(), TagExtractor(Seq("limit"))),
      GroupByNamesInAndTag("jvm.hiccup", Set(), TagExtractor(Seq())),
      GroupByPrefixAndTag("akka.http.server.", Set("port"), SuffixExtractor("akka.http.server.", Seq("port"))),
      GroupByNamesInAndTag("sql.failure", Set(), TagExtractor(Seq("statement"))),
      GroupByNamesInAndTag("sql.time", Set("statement"), TagExtractor(Seq("statement"))),
      GroupByNamesInAndTag("sql.time", Set("statement"), TagExtractor(Seq("statement"))),
      GroupByPrefixAndTag("bg.stream.", Set("streamName"), SuffixExtractor("bg.stream.", Seq("statistic"))),
    )
  }

  override protected def initialize(): Unit = {
    val kamonEnabled = config.getBoolean("kamon.enabled")
    if (kamonEnabled) {
      LOGGER.info("Kamon will be enabled")
      Kamon.reconfigure(config)
      SystemMetrics.startCollecting()
      Kamon.loadReportersFromConfig()

      //      if (config.hasPath("kamon.spm.token") && config.getString("kamon.spm.token").nonEmpty) {
      //        LOGGER.info("SPM token found, enabling Kamon spm reporter")
      //        Kamon.addReporter(new SPMReporter())
      //      } else {
      //        LOGGER.info("SPM Kamon reporter disabled as no kamon.spm.token provided")
      //      }

      if (config.getBoolean("kamon.sql.enabled")) {
        ScalikeSqlMonitoring()
      }
      LOGGER.info("Kamon enabled.")
    } else {
      LOGGER.info("Kamon disabled.")
    }
    super.initialize()
    if (kamonEnabled) {
      if (config.getBoolean("kamon.logging.enabled")) {
        LOGGER.info("Kamon Log reporter enabled.")
        Kamon.addReporter(new LoggingReporter(Logging(system, "Kamon"), createMetricSelectors))
      }
      if (config.getBoolean("kamon.prometheus.enabled")) {
        LOGGER.warn(s"Kamon Prometheus reporter enabled on port ${config.getString("kamon.prometheus.embedded-server.port")}.")
        Kamon.addReporter(new PrometheusReporter())
      }
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
