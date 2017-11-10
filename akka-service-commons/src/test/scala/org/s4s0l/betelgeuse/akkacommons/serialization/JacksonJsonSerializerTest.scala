/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-16 02:12
 *
 */
//above is a lie
package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.ConfigFactory
import org.scalatest.{FeatureSpec, Matchers}

class JacksonJsonSerializerTest extends FeatureSpec with Matchers {

//

  feature("Akka serialzation to json with jackson") {
    scenario("serializer") {
      val serializer = new JacksonJsonSerializer()
      val a = Animal("our cat", 12, Cat("black", tail = true))
      val bytes = serializer.toBinary(a)
      val ar = serializer.fromBinary(bytes, classOf[Animal]).asInstanceOf[Animal]
      assert(a == ar)
    }


    scenario("serializer - scala map") {
      val serializer = new JacksonJsonSerializer()
      val a: Map[String, Any] = Map("a" -> 1, "b" -> Map("1" -> "0"))
      val bytes = serializer.toBinary(a)
      val ar = serializer.fromBinary(bytes, classOf[Map[String, Any]]).asInstanceOf[Map[String, Any]]
      assert(a == ar)
    }


    scenario("Registering the serializer works") {
      val system = ActorSystem("JacksonJsonSerializerTest", ConfigFactory.load("JacksonJsonSerializerTest.conf"))

      val serialization = SerializationExtension.get(system)
      assert(classOf[JacksonJsonSerializer] == serialization.serializerFor(classOf[Animal]).getClass)

      system.terminate()
    }

    scenario("DepricatedTypeWithMigrationInfo") {
      val serializer = new JacksonJsonSerializer()
      val bytes = serializer.toBinary(OldType("12"))
      assert(NewType(12) == serializer.fromBinary(bytes, classOf[OldType]))
    }

    scenario("verifySerialization - no error") {
      JacksonJsonSerializer.setVerifySerialization(true)
      val serializer = new JacksonJsonSerializer()
      val a = Animal("our cat", 12, Cat("black", tail = true))
      val ow = ObjectWrapperWithTypeInfo(a)
      serializer.toBinary(ow)
    }

    scenario("verifySerialization - with error") {
      JacksonJsonSerializer.setVerifySerialization(true)
      val serializer = new JacksonJsonSerializer()
      val a = Animal("our cat", 12, Cat("black", tail = true))
      val ow = ObjectWrapperWithoutTypeInfo(a)
      intercept[JacksonJsonSerializerVerificationFailed] {
        serializer.toBinary(ow)
      }
    }

    scenario("verifySerialization - disabled") {
      JacksonJsonSerializer.setVerifySerialization(true)
      val serializer = new JacksonJsonSerializer()
      val a = Animal("our cat", 12, Cat("black", tail = true))
      val ow = ObjectWrapperWithoutTypeInfoOverrided(a)
      serializer.toBinary(ow)
    }


  }
}
