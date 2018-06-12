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

import akka.actor.ActorSystem
import akka.serialization.{Serialization, SerializationExtension}
import akka.stream.scaladsl.{Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, SourceRef}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializerTest.{Animal, Cat}
import org.scalatest.FeatureSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * @author Marcin Wielgus
  */
class PayloadTest extends FeatureSpec {

  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName, ConfigFactory.parseResources("serialization-jackson.conf"))
  implicit val serializer: Serialization = SerializationExtension(system)


  feature("Payload is serializable") {
    val serializer = new JacksonJsonSerializer()
    scenario("Is serializable in all formats") {
      JacksonJsonSerializer.verifySerialization {
        assert(!serializer.toBinary(Payload.emptyUnit).isEmpty)
        assert(!serializer.toBinary(Payload("ala ma kota")).isEmpty)
        assert(!serializer.toBinary(Payload(Array[Byte](1, 2, 3))).isEmpty)
        assert(!serializer.toBinary(Payload(ByteString(Array[Byte](1, 2, 3)))).isEmpty)
      }
    }
  }


  feature("Payload can transfer stream refs") {
    val jsonSerializer = new JacksonJsonSerializer()
    scenario("Source Ref") {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val value: Future[SourceRef[Int]] = Source.apply(List(1, 2, 3, 4))
        .runWith(StreamRefs.sourceRef())
      val ref = Await.result(value, 2.seconds)
      val payload: Payload[SourceRef[Int]] = Payload.wrap(ref)
      val bytes = jsonSerializer.simpleToString(payload)

      val deserialized = jsonSerializer.simpleFromString[Payload[SourceRef[Int]]](bytes)

      val sink = Sink.seq[Int]
      val ret = deserialized.unwrap.runWith(sink)
      val res = Await.result(ret, 2.seconds)
      assert(res == List(1, 2, 3, 4))
    }
  }


  feature("Payload can be conveniently created and deconstructed to objects using converters") {
    scenario("Creating from object with serializer and reading from them") {
      val animal = Animal("our cat", 12, Cat("black", tail = true))
      val payload: Payload[Animal] = animal
      assert(payload.asString == "{\"name\":\"our cat\",\"age\":12,\"t\":{\"color\":\"black\",\"tail\":true}}")
      val animalRead: Animal = payload.unwrap
      assert(animal == animalRead)
      assert(!payload.isEmpty)
      assert(payload.payloadSize == payload.asString.length)
    }
  }

  feature("Payload can be bytes or string, but should be usable as both") {
    scenario("Create as both string or byte") {
      val payload: Payload[String] = "somepayload"
      assert(payload.asString == "somepayload")
      assert(payload.unwrap == "somepayload")
      assert(payload.asBytes == ByteString("somepayload"))
      assert(payload.asArray sameElements Array[Byte]('s': Byte, 'o', 'm', 'e', 'p', 'a', 'y', 'l', 'o', 'a', 'd'))
      assert(payload.payloadSize == 11)

      val payload2: Payload[String] = Payload.wrap("somepayload")
      assert(payload2.asString == "somepayload")
      assert(payload2.unwrap == "somepayload")
      assert(payload2.asBytes == ByteString("somepayload"))
      assert(payload2.asArray sameElements Array[Byte]('s': Byte, 'o', 'm', 'e', 'p', 'a', 'y', 'l', 'o', 'a', 'd'))
      assert(payload2.payloadSize == 11)

      val payloadFromBytes: Payload[ByteString] = ByteString("somepayload")
      assert(payloadFromBytes.asString == "somepayload")
      assert(payloadFromBytes.unwrap == ByteString("somepayload"))
      assert(payloadFromBytes.asBytes == ByteString("somepayload"))
      assert(payloadFromBytes.asArray sameElements "somepayload".getBytes())
      assert(payloadFromBytes.payloadSize == 11)


      val payloadFromBytes2: Payload[ByteString] = Payload.wrap(ByteString("somepayload"))
      assert(payloadFromBytes2.asString == "somepayload")
      assert(payloadFromBytes2.unwrap == ByteString("somepayload"))
      assert(payloadFromBytes2.asBytes == ByteString("somepayload"))
      assert(payloadFromBytes2.asArray sameElements "somepayload".getBytes())
      assert(payloadFromBytes2.payloadSize == 11)


      val payloadFromArray: Payload[Array[Byte]] = "123".getBytes()
      assert(payloadFromArray.asString == "123")
      assert(payloadFromArray.asBytes == ByteString("123"))
      assert(payloadFromArray.asArray sameElements "123".getBytes())
      assert(payloadFromArray.unwrap sameElements "123".getBytes())
      assert(payloadFromArray.payloadSize == 3)


      val payloadFromArray2: Payload[Array[Byte]] = Payload.wrap("123".getBytes())
      assert(payloadFromArray2.asString == "123")
      assert(payloadFromArray2.asBytes == ByteString("123"))
      assert(payloadFromArray2.asArray sameElements "123".getBytes())
      assert(payloadFromArray2.unwrap sameElements "123".getBytes())
      assert(payloadFromArray2.payloadSize == 3)

    }
    scenario("Implicit conversions to string and byteString and array (deprecated)") {
      val payload: Payload[String] = "somepayload"
      val s: String = payload.asString
      val x: ByteString = payload.asBytes
      val a: Array[Byte] = payload.asArray
      assert(s == "somepayload")
      assert(x == ByteString("somepayload"))
      assert(a sameElements Array[Byte]('s': Byte, 'o', 'm', 'e', 'p', 'a', 'y', 'l', 'o', 'a', 'd'))
    }

    scenario("isEmpty checks") {
      val payload1: Payload[String] = Payload("")
      val payload2: Payload[ByteString] = Payload(ByteString(Array[Byte]()))
      val payload3 = Payload.emptyUnit

      assert(payload1.isEmpty)
      assert(payload2.isEmpty)
      assert(payload3.isEmpty)

      val payload4: Payload[String] = Payload("x")
      val payload5: Payload[ByteString] = Payload(ByteString(Array[Byte](0)))

      assert(!payload4.isEmpty)
      assert(!payload5.isEmpty)
    }

  }

}
