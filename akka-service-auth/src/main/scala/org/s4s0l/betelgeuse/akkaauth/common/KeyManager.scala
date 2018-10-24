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

package org.s4s0l.betelgeuse.akkaauth.common

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.URL
import java.nio.charset.Charset
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, PrivateKey, PublicKey}
import java.util.Base64
import java.util.regex.Pattern

import com.typesafe.config.Config

/**
  * @author Marcin Wielgus
  */
class KeyManager(implicit config: Config) {

  private def loadPEM(resource: URL) = {
    val in = resource.openStream
    val pem = new String(readAllBytes(in), Charset.forName("ISO_8859_1"))
    val parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*")
    val encoded = parse.matcher(pem).replaceFirst("$1")
    Base64.getMimeDecoder.decode(encoded)
  }

  private val kf = KeyFactory.getInstance("RSA")
  private val privateKeyPath = new URL(config.getString("bg.auth.jwt.keys.private"))
  private val publicKeyPath = new URL(config.getString("bg.auth.jwt.keys.public"))

  lazy val privateKey: PrivateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(loadPEM(privateKeyPath)))
  lazy val publicKey: PublicKey = kf.generatePublic(new X509EncodedKeySpec(loadPEM(publicKeyPath)))
  lazy val publicKeyBase64: String =
    Base64.getEncoder.encodeToString(loadPEM(publicKeyPath))

  private def readAllBytes(in: InputStream) = {
    val baos = new ByteArrayOutputStream
    val buf = new Array[Byte](1024)
    var read = 0
    while ( {
      read != -1
    }) {
      baos.write(buf, 0, read)

      read = in.read(buf)
    }
    baos.toByteArray
  }
}


object KeyManager {
  def publicKeyFromBase64(encoded: String): PublicKey = {
    val kf = KeyFactory.getInstance("RSA")
    val decoded = Base64.getDecoder.decode(encoded)
    kf.generatePublic(new X509EncodedKeySpec(decoded))
  }
}