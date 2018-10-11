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

import java.util.concurrent.ConcurrentHashMap

import kamon.Kamon
import kamon.metric.{Counter, Histogram, MeasurementUnit}
import scalikejdbc.GlobalSettings

import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */
object ScalikeSqlMonitoring {


  private lazy val taggedHistograms = new ConcurrentHashMap[String, Histogram]()
  private lazy val failCountersHistograms = new ConcurrentHashMap[String, Counter]()

  import java.util.function.{Function => JFunction}

  private implicit def toJavaFunction[A, B](f: A => B): JFunction[A, B] = (a: A) => f(a)

  def apply(): Unit = {
    GlobalSettings.taggedQueryCompletionListener =
      (statement: String, _: Seq[Any], millis: Long, tags: Seq[String]) => {
        val tagValue = getTagValue(statement, tags)
        val metric = taggedHistograms.computeIfAbsent(tagValue, _ => Kamon
          .histogram("sql.time", MeasurementUnit.time.milliseconds)
          .refine("statement", tagValue))
        metric.record(millis)

      }
    GlobalSettings.taggedQueryFailureListener =
      (statement: String, _: Seq[Any], _: Throwable, tags: Seq[String]) => {
        val tagValue = getTagValue(statement, tags)
        val metric = failCountersHistograms.computeIfAbsent(tagValue, _ => Kamon
          .counter("sql.failure", MeasurementUnit.time.milliseconds)
          .refine("statement", tagValue))
        metric.increment()
      }
  }

  private def getTagValue(statement: String, tags: Seq[String]) = {
    val calculatedTags = if (tags.nonEmpty) {
      tags
    } else {
      Seq(getTypeTag(statement))
    }
    val tagValue = calculatedTags.mkString("_")
    tagValue
  }

  private def getTypeTag(statement: String): String = {
    statement match {
      case _ if statement.startsWith("insert") => "insert"
      case _ if statement.startsWith("select") => "select"
      case _ if statement.startsWith("delete") => "delete"
      case _ if statement.startsWith("update") => "update"
      case _ if statement.startsWith("show") => "show"
      case _ if statement.startsWith("INSERT") => "insert"
      case _ if statement.startsWith("SELECT") => "select"
      case _ if statement.startsWith("DELETE") => "delete"
      case _ if statement.startsWith("UPDATE") => "update"
      case _ if statement.startsWith("SHOW") => "show"
      case _ => "other"
    }
  }

}

