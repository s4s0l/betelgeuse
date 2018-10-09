package org.s4s0l.betelgeuse.akkaauth

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.regex.Pattern
import java.util.{Base64, Date, UUID}

import pdi.jwt.JwtBase64
import pdi.jwt.exceptions.JwtLengthException


/**
  * @author Marcin Wielgus
  */
object SmokeTest extends App {

  private def loadPEM(resource: String) = {
    val url = getClass.getResource(resource)
    val in = url.openStream
    val pem = new String(readAllBytes(in), Charset.forName("ISO_8859_1"))
    val parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*")
    val encoded = parse.matcher(pem).replaceFirst("$1")
    Base64.getMimeDecoder.decode(encoded)
  }

  val kf = KeyFactory.getInstance("RSA")
  val cf = CertificateFactory.getInstance("X.509")
  val key = kf.generatePrivate(new PKCS8EncodedKeySpec(loadPEM("/private_key.pem")))
  val pub = kf.generatePublic(new X509EncodedKeySpec(loadPEM("/public_key.pem")))

  //  val crt = cf.generateCertificate(getClass.getResourceAsStream("test.crt"))
  def readAllBytes(in: InputStream) = {
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

  def splitToken(token: String): (String, String, String, String, String) = {
    val parts = token.split("\\.")

    val signature = parts.length match {
      case 2 => ""
      case 3 => parts(2)
      case _ => throw new JwtLengthException(s"Expected token [$token] to be composed of 2 or 3 parts separated by dots.")
    }

    (parts(0), JwtBase64.decodeString(parts(0)), parts(1), JwtBase64.decodeString(parts(1)), signature)
  }

  val jwt = new CustomJwtEncoder(pub, key)

  val auth = AuthenticationInfo(
    UUID.randomUUID().toString,
    "admin",
    List("role1", "role2"),
    System.currentTimeMillis() + 60 * 60 * 1000,
    Map("custom" -> "attribute")
  )

  val encoded = jwt.encode(auth, new Date().getTime, null)
  println(encoded)
  private val tuple: (String, String, String, String, String) = splitToken(encoded)
  println(tuple)
  val fake = JwtBase64.encodeString(tuple._2.replace("jwt", "JWT")) +
    "." + tuple._3 + "." + tuple._5
  jwt.decode(encoded, null) match {
    case scala.util.Success(decoded) =>
      println(decoded)
    case scala.util.Failure(exception) =>
      println("fail")
      exception.printStackTrace()
  }


}
