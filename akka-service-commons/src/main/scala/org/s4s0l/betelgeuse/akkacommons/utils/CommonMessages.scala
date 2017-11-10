/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */



package org.s4s0l.betelgeuse.akkacommons.utils

import java.util.UUID

import scala.concurrent.duration
import scala.concurrent.duration.{Deadline, Duration, FiniteDuration}
import scala.language.implicitConversions

/**
  * Common messages that are unlikely to by modified so in migration there will be
  * slight chance they will be unchanged
  *
  * @author Marcin Wielgus
  */
object CommonMessages {

  sealed trait CommonMessage

  trait HeaderAccessors {

    val headers: Map[String, String]

    def contains(key: String): Boolean = headers.contains(key)

    def apply(key: String): String = headers.apply(key)

    def get(key: String): Option[String] = headers.get(key)

    def conversationId: String = headers(HEADER_CONVERSATION_ID)

    def sequence: Int = sequenceOpt.get

    def sequenceOpt: Option[Int] = headers.get(HEADER_SEQUENCE).map(_.toInt)

    def last: Boolean = lastOpt.get

    def lastOpt: Option[Boolean] = headers.get(HEADER_SEQUENCE_LAST).map(_.toBoolean).orElse(
      sequenceOpt.map(_ => false)
    )

    def isFailed: Boolean = failedOpt.isDefined

    def failedOpt: Option[Int] = headers.get(HEADER_FAILED).map(_.toInt)

    def failed: Int = failedOpt.get

    def conversationIdOpt: Option[String] = headers.get(HEADER_CONVERSATION_ID)

    def correlationId: String = headers(HEADER_CORRELATION_ID)

    def correlationIdOpt: Option[String] = headers.get(HEADER_CORRELATION_ID)

    def subjectId: String = headers(HEADER_SUBJECT_ID)

    def subjectIdOpt: Option[String] = headers.get(HEADER_SUBJECT_ID)

    def scopeId: String = headers(HEADER_SCOPE_ID)

    def scopeIdOpt: Option[String] = headers.get(HEADER_SCOPE_ID)

    def traces: Seq[(String, String)] = {
      val traceIds = headers.get(HEADER_TRACE_IDS).map(_.split(',').toSeq).getOrElse(Seq())
      val traceTargets = headers.get(HEADER_TRACE_TARGETS).map(_.split(',').toSeq).getOrElse(Seq())
      traceIds.zip(traceTargets)
    }

    def ttl: Deadline = Deadline(Duration(headers(HEADER_TTL).toLong, duration.MILLISECONDS))

    def ttlOpt: Option[Deadline] = headers.get(HEADER_TTL).map(it => Deadline(Duration(it.toLong, duration.MILLISECONDS)))

  }


  trait HeaderSetter[T <: HeaderAccessors] {
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

    def withConversationId(as: String): T = withHeader(HEADER_CONVERSATION_ID, as)

    def withCorrelationId(as: String): T = withHeader(HEADER_CORRELATION_ID, as)

    def withSubjectId(as: String): T = withHeader(HEADER_SUBJECT_ID, as)

    def withTtl(as: FiniteDuration): T = withHeader(HEADER_TTL, as.fromNow.time.toMillis.toString)

    def withConversationIdFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_CONVERSATION_ID, as)

    def withCorrelationIdFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_CORRELATION_ID, as)

    def withSubjectIdFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SUBJECT_ID, as)

    def withTtlFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_TTL, as)

    def withLast(x: Boolean = true): T = withHeader(HEADER_SEQUENCE_LAST, x.toString)

    def withLastFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SEQUENCE_LAST, as)

    def withScope(x: String): T = withHeader(HEADER_SCOPE_ID, x.toString)

    def withScopeFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SCOPE_ID, as)


    def withFailed(x: Int = 500): T = withHeader(HEADER_FAILED, x.toString)

    def withFailedFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_FAILED, as)

    def withSequence(seq: Int): T = withHeader(HEADER_SEQUENCE, seq.toString)


    def withSequenceFrom(as: Map[String, String]): T = withHeaderFrom(HEADER_SEQUENCE, as)

  }

  case class Headers(headers: Map[String, String]) extends HeaderAccessors with HeaderSetter[Headers] {

    override def withHeaders(map: Map[String, String]): Headers = Headers(headers ++ map)

    override def withHeader(k: String, v: String): Headers = Headers(headers + (k -> v))
  }

  object Headers {

    implicit def toMap(headers: Headers): Map[String, String] = headers.headers

    implicit def fromMap(map: Map[String, String]): Headers = new Headers(map)

    def apply(): Headers = new Headers(Map())

  }

  object Message {
    implicit def toMap(msg: Message): Map[String, String] = msg.headers

  }

  @SerialVersionUID(2L)
  final case class Message(target: String, id: String, headers: Map[String, String], payload: String)
    extends CommonMessage
      with HeaderAccessors
      with HeaderSetter[Message] {

    def this(target: String, payload: String) = this(target, UUID.randomUUID().toString, Map(), payload)

    override def withHeaders(map: Map[String, String]): Message = copy(headers = headers ++ map)

    override def withHeader(key: String, value: String): Message = {
      copy(headers = headers + (key -> value))
    }

    def response(newTarget: String, newPayload: String, newHeaders: Map[String, String] = Map()): Message = {
      Message(newTarget, UUID.randomUUID().toString,
        (followHeaders + (HEADER_CORRELATION_ID -> id)) ++ newHeaders, newPayload)
    }

    def forward(newTarget: String, newPayload: String = payload, newHeaders: Map[String, String] = Map()): Message = {
      Message(newTarget, UUID.randomUUID().toString,
        followHeaders ++ newHeaders, newPayload)
    }

    def followHeaders: Map[String, String] = {
      var frwrded = headers.filter(it => FORWARDED_HEADERS.contains(it._1))
      if (frwrded.contains(HEADER_TRACE_IDS)) {
        frwrded = frwrded + (HEADER_TRACE_IDS -> (frwrded(HEADER_TRACE_IDS) + "," + id))
      } else {
        frwrded = frwrded + (HEADER_TRACE_IDS -> id)
      }
      if (frwrded.contains(HEADER_TRACE_TARGETS)) {
        frwrded = frwrded + (HEADER_TRACE_TARGETS -> (frwrded(HEADER_TRACE_TARGETS) + "," + target))
      } else {
        frwrded = frwrded + (HEADER_TRACE_TARGETS -> target)
      }
      frwrded
    }


  }


  val HEADER_CONVERSATION_ID = "conversationId"
  val HEADER_SCOPE_ID = "scopeId"
  val HEADER_SEQUENCE = "seq"
  val HEADER_FAILED = "failed"
  val HEADER_SEQUENCE_LAST = "seq-last"
  val HEADER_CORRELATION_ID = "correlationId"
  val HEADER_SUBJECT_ID = "subjectId"
  val HEADER_TRACE_IDS = "traceIds"
  val HEADER_TRACE_TARGETS = "traceTargets"
  val HEADER_USER_ID = "userId"
  val HEADER_TTL = "ttl"

  val FORWARDED_HEADERS = Seq(
    HEADER_CONVERSATION_ID,
    HEADER_TRACE_IDS,
    HEADER_TRACE_TARGETS,
    HEADER_SUBJECT_ID,
    HEADER_SCOPE_ID,
    HEADER_USER_ID
  )
}
