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



//THEaboe is a lie!!!! taken from https://github.com/NextGenTel/akka-tools
package org.s4s0l.betelgeuse.akkacommons.serialization

import java.io.NotSerializableException

import akka.serialization.{Serialization, Serializer}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.slf4j.LoggerFactory

/**
  * taken from https://github.com/NextGenTel/akka-tools
  */
object JacksonJsonSerializer {

  def identifier: Int = 67567521

  def get(system: Serialization): Option[JacksonJsonSerializer] = {
    system.serializerByIdentity.get(identifier).map(_.asInstanceOf[JacksonJsonSerializer])
  }

  private var _objectMapper: Option[ObjectMapper] = {
    val om = new ObjectMapper()
    om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    om.registerModule(new DefaultScalaModule)
    Some(om)
  }

  // Should only be used during testing
  // When true, all objects being serialized are also deserialized and compared
  private var verifySerialization: Boolean = false

  def setObjectMapper(preConfiguredObjectMapper: ObjectMapper): Unit = {
    _objectMapper = Some(preConfiguredObjectMapper)
  }

  def setVerifySerialization(verifySerialization: Boolean): Unit = {
    this.verifySerialization = verifySerialization
    if (verifySerialization) {
      val logger = LoggerFactory.getLogger(getClass)
      logger.warn("*** Performance-warning: All objects being serialized are also deserialized and compared. Should only be used during testing")
    }
  }

  protected def objectMapper(): ObjectMapper = {
    _objectMapper.getOrElse(throw new Exception(getClass.toString + " has not been with an initialized with an objectMapper. You must call init(objectMapper) before using the serializer"))
  }
}

class JacksonJsonSerializerException(errorMsg: String, cause: Throwable) extends NotSerializableException() {
  // Must extend NotSerializableException so that we can prevent akka-remoting connections being dropped
  override def getMessage: String = errorMsg

  override def getCause: Throwable = cause
}

class JacksonJsonSerializerVerificationFailed(errorMsg: String) extends JacksonJsonSerializerException(errorMsg, null)

class JacksonJsonSerializer extends Serializer {
  private val logger = LoggerFactory.getLogger(getClass)

  import JacksonJsonSerializer._

  // The serializer id has to have this exact value to be equal to the old original implementation
  override def identifier: Int = JacksonJsonSerializer.identifier

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val clazz: Class[_] = manifest.get

    val o = objectMapper().readValue(bytes, clazz).asInstanceOf[AnyRef]

    o match {
      case d: DepricatedTypeWithMigrationInfo =>
        val m = d.convertToMigratedType()
        m
      case _: JacksonJsonSerializableButNotDeserializable =>
        throw new Exception("The type " + o.getClass + " is not supposed to be deserializable since it extends JacksonJsonSerializableButNotDeserializable")
      case x: AnyRef => x
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    try {
      val bytes: Array[Byte] = objectMapper().writeValueAsBytes(o)
      if (verifySerialization) {
        doVerifySerialization(o, bytes)
      }
      bytes
    } catch {
      case e: JacksonJsonSerializerException =>
        throw e
      case e: Exception =>
        throw new JacksonJsonSerializerException(e.getMessage, e)
    }
  }

  private def doVerifySerialization(originalObject: AnyRef, bytes: Array[Byte]): Unit = {
    if (originalObject.isInstanceOf[JacksonJsonSerializableButNotDeserializable]) {
      if (logger.isDebugEnabled) {
        logger.debug("Skipping doVerifySerialization: " + originalObject.getClass)
      }
      return
    }
    val deserializedObject: AnyRef = fromBinary(bytes, originalObject.getClass)
    if (!(originalObject == deserializedObject)) {
      throw new JacksonJsonSerializerVerificationFailed("Serialization-verification failed.\n" + "original:     " + originalObject.toString + "\n" + "deserialized: " + deserializedObject.toString)
    }
  }
}
