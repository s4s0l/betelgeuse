/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
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

package org.s4s0l.betelgeuse.akkacommons.patterns.message


import com.fasterxml.jackson.annotation.JsonIgnore
import org.s4s0l.betelgeuse.utils.UuidUtils

import scala.concurrent.duration
import scala.concurrent.duration.{Deadline, Duration, FiniteDuration}
import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */
object MessageHeaders {

  val HEADER_FAILED: String = "failed"
  val HEADER_CREATION_TIME: String = "timestamp"
  val HEADER_SEQUENCE_LAST: String = "seqLast"
  val HEADER_SEQUENCE_NUMBER: String = "seqNum"
  val HEADER_SEQUENCE_ID: String = "seqId"
  val HEADER_CORRELATION_ID: String = "correlationId"
  val HEADER_TRACE_IDS: String = "traceIds"
  val HEADER_TRACE_TARGETS: String = "traceTargets"
  val HEADER_USER_ID: String = "userId"
  val HEADER_TTL: String = "ttl"

  val FORWARDED_HEADERS: Seq[String] = Seq(
    HEADER_TRACE_IDS,
    HEADER_TRACE_TARGETS,
    HEADER_USER_ID,
    HEADER_SEQUENCE_LAST,
    HEADER_SEQUENCE_NUMBER,
    HEADER_SEQUENCE_ID
  )

  val RESPONSE_HEADERS: Seq[String] = Seq(
    HEADER_TRACE_IDS,
    HEADER_TRACE_TARGETS,
    HEADER_USER_ID
  )



  trait HeaderAccessors {

    val headers: Map[String, String]

    def contains(key: String): Boolean = headers.contains(key)

    def apply(key: String): String = headers.apply(key)

    def get(key: String): Option[String] = headers.get(key)

    def sequenceNumber: Int = sequenceNumberOpt.get

    def sequenceNumberOpt: Option[Int] = headers.get(HEADER_SEQUENCE_NUMBER).map(_.toInt)

    def sequenceId: String = sequenceIdOpt.get

    def sequenceIdOpt: Option[String] = headers.get(HEADER_SEQUENCE_ID)

    def sequenceLast: Boolean = sequenceLastOpt.get

    def sequenceLastOpt: Option[Boolean] = headers.get(HEADER_SEQUENCE_LAST).map(_.toBoolean).orElse(
      sequenceNumberOpt.map(_ => false)
    )

    def sequence: SequenceInfo = sequenceOpt.get

    def sequenceOpt: Option[SequenceInfo] =
      for (id <- sequenceIdOpt;
           number <- sequenceNumberOpt;
           last <- sequenceLastOpt)
        yield SequenceInfo(id, number, last)

    @JsonIgnore
    def isFailed: Boolean = failedOpt.isDefined

    def failedOpt: Option[Int] = headers.get(HEADER_FAILED).map(_.toInt)

    def failed: Int = failedOpt.get

    def correlationId: String = headers(HEADER_CORRELATION_ID)

    def userId: String = headers(HEADER_USER_ID)

    def ttl: Deadline = Deadline.now + ttlAsDurationLeft

    def ttlAsDurationLeft: FiniteDuration = Duration(headers(HEADER_TTL).toLong + timestamp - System.currentTimeMillis(), duration.MILLISECONDS)

    def correlationIdOpt: Option[String] = headers.get(HEADER_CORRELATION_ID)

    def traces: Seq[(String, String)] = {
      val traceIds = headers.get(HEADER_TRACE_IDS).map(_.split(',').toSeq).getOrElse(Seq())
      val traceTargets = headers.get(HEADER_TRACE_TARGETS).map(_.split(',').toSeq).getOrElse(Seq())
      traceIds.zip(traceTargets)
    }

    def timestamp: Long = headers(HEADER_CREATION_TIME).toLong

    def ttlOpt: Option[Deadline] = headers.get(HEADER_TTL).map(it => Deadline(Duration(it.toLong, duration.MILLISECONDS)))

  }

  trait HeaderSetter[T <: HeaderAccessors with HeaderSetter[T]] {
    accessors: HeaderAccessors =>

    def withHeaders(map: Map[String, String]): T

    def withHeader(key: String, value: String): T

    /**
      * sets given geader if found in given map
      */
    def withHeaderFrom(key: String, from: Map[String, String]): T = {
      from.get(key).map { it =>
        withHeader(key, it)
      }.getOrElse(withHeaders(Map()))
    }

    def header(key: (String, String)): T = withHeader(key._1, key._2)

    def withCorrelationId(as: String): T = withHeader(HEADER_CORRELATION_ID, as)

    def withCorrelationIdFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_CORRELATION_ID, as)

    def withTtl(as: FiniteDuration): T = withHeader(HEADER_TTL, as.toMillis.toString)

    def withTtlFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_TTL, as)

    def withSequence(number: Int, id: String = UuidUtils.timeBasedUuid().toString, last: Boolean = false): T =
      withSequenceId(id).withSequenceNumber(number).withSequenceLast(last)

    def withSequenceLast(x: Boolean = true): T = withHeader(HEADER_SEQUENCE_LAST, x.toString)

    def withFailed(x: Int = 500): T = withHeader(HEADER_FAILED, x.toString)

    def withFailedFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_FAILED, as)

    def withSequenceNumber(seq: Int): T = withHeader(HEADER_SEQUENCE_NUMBER, seq.toString)

    def withSequenceId(seq: String): T = withHeader(HEADER_SEQUENCE_ID, seq)

    def withSequenceFrom(as: Map[String, String]): T = withSequenceIdFrom(as).withSequenceNumberFrom(as).withSequenceLastFrom(as)

    def withSequenceLastFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SEQUENCE_LAST, as)

    def withSequenceNumberFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SEQUENCE_NUMBER, as)

    def withSequenceIdFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SEQUENCE_ID, as)

    def withUser(userId: String): T = withHeader(HEADER_USER_ID, userId)

    def withUserFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_USER_ID, as)


  }

  case class Headers(headers: Map[String, String]) extends HeaderAccessors with HeaderSetter[Headers] {

    override def withHeaders(map: Map[String, String]): Headers = Headers(headers ++ map)

    override def withHeader(k: String, v: String): Headers = Headers(headers + (k -> v))
  }

  final case class SequenceInfo(id: String, number: Int, last: Boolean)

  object Headers {

    implicit def toMap(headers: Headers): Map[String, String] = headers.headers

    implicit def fromMap(map: Map[String, String]): Headers = new Headers(map)

    def apply(): Headers = new Headers(Map())

  }

}
