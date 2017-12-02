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

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import org.scalatest.FeatureSpec

import scala.concurrent.duration
import scala.concurrent.duration.Duration

/**
  * @author Marcin Wielgus
  */
class MessageTest extends FeatureSpec {

  feature("Message with headers and convenient api to manipulate message") {
    scenario("Headers are being set") {
      val message = Message("target", "body")
      assert(message.id.length > 0)
      assert(message.payload.asString == "body")
      assert(message.target == "target")
      assert(message.timestamp > 0)
      assert(message.headers.size == 1)

      assert(message.withHeader("a", "b")("a") == "b")
      assert(message.withHeaders(Map("a" -> "b"))("a") == "b")
      assert(message.withCorrelationId("id").correlationId == "id")
      assert(message.withCorrelationIdFrom(Map(MessageHeaders.HEADER_CORRELATION_ID -> "b")).correlationId == "b")
      assert(message.withHeaders(message.withSequenceNumber(1)).sequenceNumber == 1)
      assert(message.withHeaderFrom(MessageHeaders.HEADER_TTL, message.withTtl(Duration(1l, duration.SECONDS))).ttl.hasTimeLeft())
      assert(message.withHeaderFrom(MessageHeaders.HEADER_TTL, message.withTtl(Duration(-1l, duration.SECONDS))).ttl.isOverdue())
    }

    scenario("Forwarded messages contains proper headers based on original message") {
      val message = Message("target", "body")
        .withHeader("header", "value") //custom header
        .withUser("userId") // standard forwarded header
      Thread.sleep(1) //we need to wait at least 1 ms to be sure timestamp is changed in forwarded message
      val forwarded = message.forward("target2")(() => Seq("header"))
      assert(forwarded.payload == message.payload)
      assert(forwarded.id != message.id)
      assert(forwarded("header") == "value")
      assert(forwarded.userId == "userId")
      assert(forwarded.traces == Seq((message.id, message.target)))
      assert(forwarded.timestamp != message.timestamp)

      val forwarded2 = forwarded.forward("target3")(Message.defaultForward)
      assert(forwarded2.traces == Seq((message.id, message.target), (forwarded.id, forwarded.target)))
    }

    scenario("Api is extensible via implicits with no need to extend anything") {
      import MessageTestCustomExtension._
      val message = Message("target", "body").withA("avalue").withB("bvalue")

      assert(message.a == "avalue")
      assert(message.b == "bvalue")

      val forwarded = message.forward("target2")
      assert(forwarded.b == "bvalue")
      assert(forwarded.aOpt.isEmpty)


    }


    scenario("Api supporting sequences works") {
      val message = Message("test", "test").withSequence(1)
      assert(message.sequenceIdOpt.isDefined)
      assert(message.sequenceNumberOpt.isDefined)
      assert(message.sequenceLastOpt.isDefined)
      assert(message.sequenceOpt.isDefined)
      assert(message.sequence.number == 1)
      assert(!message.sequence.last)

      val message2 = Message("test", "test").withSequence(5, "aaaa", last = true)
      assert(message2.sequenceId == "aaaa")
      assert(message2.sequenceNumber == 5)
      assert(message2.sequence.last)
    }
  }
}
