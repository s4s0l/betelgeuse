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

  private val dateFormat = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .toFormatter()

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    //    println("============")
    //    snapshot.metrics.rangeSamplers.foreach(println)
    //    snapshot.metrics.histograms.foreach(println)
    //    snapshot.metrics.gauges.foreach(println)
    //    snapshot.metrics.counters.foreach(println)
    //    println("============")

    val metrics: Seq[ReporterMetric] = snapshot.metrics.counters.map(ReporterMetricValue) ++
      snapshot.metrics.gauges.map(ReporterMetricValue) ++
      snapshot.metrics.histograms.map(ReporterMetricDistribution) ++
      snapshot.metrics.rangeSamplers.map(ReporterMetricDistribution)
    val grouped = LoggingReporter.groupBy(metrics, selectors)
    val toBePrinted = LoggingReporter.printAll(grouped).toSeq.sorted
    val fromTime = timeFormat.format(snapshot.from.atZone(ZoneId.systemDefault()))
    val toTime = timeFormat.format(snapshot.to.atZone(ZoneId.systemDefault()))
    val date = dateFormat.format(snapshot.to.atZone(ZoneId.systemDefault()))
    val prefix = s"$date INFO "
    val fullReport = toBePrinted.mkString(s"Metrics report: from $fromTime to $toTime\n$prefix", s"\n$prefix", "\n")
    log.info(fullReport)
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
      f"[$nameToUse%20s.evt = ${md.distribution.count}%4d, " +
        f"$nameToUse%20s.min = ${md.distribution.min}%10d, " +
        f"$nameToUse%20s.max = ${md.distribution.max}%10d, " +
        f"$nameToUse%20s.med = ${md.distribution.percentile(0.5).value}%10d, " +
        f"$nameToUse%20s .99 = ${md.distribution.percentile(0.99).value}%10d${printUnit(md.unit)}]"
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
      f"[$nameToUse%18s = ${md.value}%10d${printUnit(md.unit)}]"
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

        val res = name.iterator.sliding(2).collect {
          case a :: b :: Nil if !a.isLetterOrDigit && b.isLetterOrDigit => b
        }.mkString(name.substring(0, 1), "", ".")
        if (res.length > 5) {
          res.substring(res.length - 5, res.length)
        } else {
          res
        }
      }
    }

    groupedMetrics.map { it =>
      val ((name, selector), metrics) = it
      val shortcut = shorten(name)
      val values = metrics.map { metric =>
        (shortcut + selector.displayExtractor.extractName(metric), metric)
      }.sortBy(_._1).map { it => it._2.print(it._1) }
      f"[$name%-100s][M] - ${values.mkString(", ")}"
    }
  }

}
