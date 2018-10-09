package org.s4s0l.betelgeuse.akkaauth

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import java.util.regex.Pattern

/**
  * @author Marcin Wielgus
  */
object Keys {
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
}
