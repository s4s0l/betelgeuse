/*
 * Copyright© 2018 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import akka.util.ByteString
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializerTest.{Animal, Cat}
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializer, SimpleSerializer}
import org.scalatest.FeatureSpec

/**
  * @author Marcin Wielgus
  */
class PayloadTest extends FeatureSpec {

  feature("Payload is serializable") {
    val serializer = new JacksonJsonSerializer()
    scenario("Is serializable in all formats") {
      JacksonJsonSerializer.verifySerialization {
        assert(!serializer.toBinary(Payload.empty).isEmpty)
        assert(!serializer.toBinary(Payload("ala ma kota")).isEmpty)
        assert(!serializer.toBinary(Payload(Array[Byte](1, 2, 3))).isEmpty)
        assert(!serializer.toBinary(Payload(ByteString(Array[Byte](1, 2, 3)))).isEmpty)
      }
    }
  }


  feature("Payload can be conveniently created and deconstructed to objects using converters") {
    scenario("Creating from object with serializer and reading from them") {
      implicit val serializer: SimpleSerializer = new JacksonJsonSerializer()
      val animal = Animal("our cat", 12, Cat("black", tail = true))
      val payload = Payload(animal)
      assert(payload.asString == "{\"name\":\"our cat\",\"age\":12,\"t\":{\"color\":\"black\",\"tail\":true}}")
      val animalRead: Animal = payload.asObject[Animal]
      assert(animal == animalRead)
      assert(!payload.isEmpty)
      assert(payload.payloadSize == payload.asString.length)
    }
  }

  feature("Payload can be bytes or string, but should be usable as both") {
    scenario("Create as both string or byte") {
      val payload: Payload = "somepayload"
      assert(payload.asString == "somepayload")
      assert(payload.asBytes == ByteString("somepayload"))
      assert(payload.asArray sameElements Array[Byte]('s': Byte, 'o', 'm', 'e', 'p', 'a', 'y', 'l', 'o', 'a', 'd'))
      assert(payload.payloadSize == 11)

      val payloadFromBytes: Payload = ByteString("somepayload")
      assert(payloadFromBytes.asString == "somepayload")
      assert(payloadFromBytes.asBytes == ByteString("somepayload"))
      assert(payloadFromBytes.asArray sameElements "somepayload".getBytes())
      assert(payloadFromBytes.payloadSize == 11)


      val payloadFromArray: Payload = "123".getBytes()
      assert(payloadFromArray.asString == "123")
      assert(payloadFromArray.asBytes == ByteString("123"))
      assert(payloadFromArray.asArray sameElements "123".getBytes())
      assert(payloadFromArray.payloadSize == 3)

    }
    scenario("Implicit conversions to string and byteString and array") {
      val payload: Payload = "somepayload"
      val s: String = payload
      val x: ByteString = payload
      val a: Array[Byte] = payload
      assert(s == "somepayload")
      assert(x == ByteString("somepayload"))
      assert(a sameElements Array[Byte]('s': Byte, 'o', 'm', 'e', 'p', 'a', 'y', 'l', 'o', 'a', 'd'))
    }

    scenario("isEmpty checks") {
      val payload1: Payload = Payload("")
      val payload2: Payload = Payload(ByteString(Array[Byte]()))
      val payload3: Payload = Payload.empty

      assert(payload1.isEmpty)
      assert(payload2.isEmpty)
      assert(payload3.isEmpty)

      val payload4: Payload = Payload("x")
      val payload5: Payload = Payload(ByteString(Array[Byte](0)))

      assert(!payload4.isEmpty)
      assert(!payload5.isEmpty)
    }

  }

}
