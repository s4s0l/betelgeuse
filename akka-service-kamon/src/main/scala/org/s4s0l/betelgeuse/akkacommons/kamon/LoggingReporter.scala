
package org.s4s0l.betelgeuse.akkacommons.kamon

import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder

import akka.event.LoggingAdapter
import com.typesafe.config.Config
import kamon.MetricReporter
import kamon.metric.{MeasurementUnit, MetricDistribution, MetricValue, PeriodSnapshot}
import org.s4s0l.betelgeuse.akkacommons.kamon.LoggingReporter.{ReporterMetric, ReporterMetricDistribution, ReporterMetricValue, Selector}

/**
  * @author Marcin Wielgus
  */
class LoggingReporter(log: LoggingAdapter, selectors: Seq[Selector]) extends MetricReporter {

  private val timeFormat = new DateTimeFormatterBuilder()
    .appendPattern("HH:mm:ss")
    .toFormatter()

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val metrics: Seq[ReporterMetric] = snapshot.metrics.counters.map(ReporterMetricValue) ++
      snapshot.metrics.gauges.map(ReporterMetricValue) ++
      snapshot.metrics.histograms.map(ReporterMetricDistribution) ++
      snapshot.metrics.rangeSamplers.map(ReporterMetricDistribution)
    val grouped = LoggingReporter.groupBy(metrics, selectors)
    val toBePrinted = LoggingReporter.printAll(grouped).toSeq.sorted
    val fromStr = timeFormat.format(snapshot.from.atZone(ZoneId.systemDefault()))
    val toStr = timeFormat.format(snapshot.to.atZone(ZoneId.systemDefault()))
    toBePrinted.foreach { it =>
      log.info(s"Metric in $fromStr - $toStr: $it ")
    }

  }

  override def start(): Unit = {}

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {}
}

object LoggingReporter {

  sealed trait ReporterMetric {
    def name: String

    def tags: Map[String, String]

    def print(nameToUse: String): String
  }

  case class ReporterMetricDistribution(md: MetricDistribution) extends ReporterMetric {
    override def name: String = md.name

    override def tags: Map[String, String] = md.tags

    override def print(nameToUse: String): String = {
      s"[$nameToUse.min = ${md.distribution.min}, " +
        s"$nameToUse.max = ${md.distribution.max}, " +
        s"$nameToUse.med = ${md.distribution.percentile(0.5).value}, " +
        s"$nameToUse.99 = ${md.distribution.percentile(0.99).value}${printUnit(md.unit)}]"
    }
  }

  private def printUnit(mu: MeasurementUnit): String = {
    (mu.magnitude.name, mu.dimension.name) match {
      case ("byte", _) => " bytes"
      case ("none", "none") => ""
      case ("milliseconds", "time") => " ms"
      case ("nanoseconds", "time") => " ns"
      case (a, b) => s" ??($a, $b)??"
    }
  }

  case class ReporterMetricValue(md: MetricValue) extends ReporterMetric {
    override def name: String = md.name

    override def tags: Map[String, String] = md.tags

    override def print(nameToUse: String): String = {
      s"[$nameToUse = ${md.value}${printUnit(md.unit)}]"
    }
  }

  sealed trait Selector {
    def matches(reporterMetric: ReporterMetric): Boolean

    def displayExtractor: DisplayExtractor

    def extractName(reporterMetric: ReporterMetric): String

  }

  private case object DefaultSelector extends Selector {
    override def matches(reporterMetric: ReporterMetric): Boolean = true

    override def displayExtractor: DisplayExtractor = throw new RuntimeException("do not use")

    override def extractName(reporterMetric: ReporterMetric): String = reporterMetric.name
  }

  object GroupByNamesInAndTag {
    def apply(name: String, groupingTags: Set[String], displayExtractor: DisplayExtractor, inverseName: Boolean): GroupByNamesInAndTag = {
      new GroupByNamesInAndTag(name, Set(name), groupingTags, displayExtractor, inverseName)
    }

    def apply(name: String, groupingTags: Set[String], displayExtractor: DisplayExtractor): GroupByNamesInAndTag = {
      new GroupByNamesInAndTag(name, Set(name), groupingTags, displayExtractor)
    }

    def apply(outputName: String, names: Set[String], groupingTags: Set[String], displayExtractor: DisplayExtractor): GroupByNamesInAndTag = {
      new GroupByNamesInAndTag(outputName, names, groupingTags, displayExtractor)

    }
  }

  case class GroupByNamesInAndTag(outputName: String, names: Set[String], groupingTags: Set[String], displayExtractor: DisplayExtractor, inverseName: Boolean = false) extends Selector {
    override def matches(reporterMetric: ReporterMetric): Boolean = {
      names.contains(reporterMetric.name) && groupingTags.forall(it => reporterMetric.tags.contains(it))
    }

    override def extractName(reporterMetric: ReporterMetric): String = {
      val tagNames = groupingTags.map(it => reporterMetric.tags(it)).mkString(".")
      val ret = if (inverseName) {
        s"$tagNames.$outputName"
      }
      else {
        s"$outputName.$tagNames"
      }
      trimDots(ret)
    }

  }

  case class GroupByPrefixAndTag(namePrefix: String, groupingTags: Set[String], displayExtractor: DisplayExtractor, inverseName: Boolean = false) extends Selector {
    override def matches(reporterMetric: ReporterMetric): Boolean = {
      reporterMetric.name.startsWith(namePrefix) && groupingTags.forall(it => reporterMetric.tags.contains(it))
    }

    override def extractName(reporterMetric: ReporterMetric): String = {
      val tagNames = groupingTags.map(it => reporterMetric.tags(it)).mkString(".")
      val ret = if (inverseName) {
        s"$tagNames.$namePrefix"
      }
      else {
        s"$namePrefix$tagNames"
      }
      trimDots(ret)
    }
  }

  private def trimDots(lin: String): String = {
    val start = lin.startsWith(".")
    val end = lin.endsWith(".")
    lin.substring(if (start) 1 else 0, if (end) lin.length - 1 else lin.length)
  }


  sealed trait DisplayExtractor {
    def extractName(reporterMetric: ReporterMetric): String
  }

  case class SuffixExtractor(prefix: String, tags: Seq[String], inverse: Boolean = false) extends DisplayExtractor {
    override def extractName(reporterMetric: ReporterMetric): String = {
      val tagsValues = tags.flatMap(it => reporterMetric.tags.get(it)).mkString(".")
      val ret = if (reporterMetric.name.startsWith(prefix)) {
        val suffix = reporterMetric.name.substring(prefix.length)
        if (inverse) {
          s"$tagsValues.$suffix"
        } else {
          s"$suffix.$tagsValues"
        }
      } else {
        tagsValues
      }
      trimDots(ret)
    }
  }

  case class TagExtractor(tags: Seq[String]) extends DisplayExtractor {
    override def extractName(reporterMetric: ReporterMetric): String = {
      val ret = tags.flatMap(it => reporterMetric.tags.get(it)).mkString(".")
      trimDots(ret)
    }
  }


  def groupBy(metrics: Seq[ReporterMetric], selectors: Seq[Selector]): Map[(String, Selector), Seq[ReporterMetric]] = {
    metrics.groupBy { metric =>
      val selectorFound = selectors.find(it => it.matches(metric))
        .getOrElse(DefaultSelector)
      (selectorFound.extractName(metric), selectorFound)
    }.filter(_._1._2 != DefaultSelector)
  }

  def printAll(groupedMetrics: Map[(String, Selector), Seq[ReporterMetric]]): Iterable[String] = {
    def shorten(name: String): String = {
      if (name.isEmpty) {
        name
      } else {

        name.iterator.sliding(2).collect {
          case a :: b :: Nil if !a.isLetterOrDigit && b.isLetterOrDigit => b
        }.mkString(name.substring(0, 1), "", ".")
      }
    }

    groupedMetrics.map { it =>
      val ((name, selector), metrics) = it
      val shortcut = shorten(name)
      val values = metrics.map { metric =>
        metric.print(shortcut + selector.displayExtractor.extractName(metric))
      }
      s"$name: ${values.mkString(", ")}"
    }
  }

}
