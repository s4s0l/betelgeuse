package org.s4s0l.betelgeuse.akkaauth.manager.impl

import org.scalatest.{FeatureSpec, FunSuite, Matchers}

class BcryptProviderTest extends FeatureSpec with Matchers {

  feature("BcryptProvider"){
    scenario("different hashes for password match same secret"){
      val hasher = new BcryptProvider(11)
      val secret = "password"

      val hash = hasher.hashPassword(secret)
      val hash2 = hasher.hashPassword(secret)

      hash should not be equal(hash2)

      hasher.checkPassword(hash, secret) shouldBe true
      hasher.checkPassword(hash2, secret) shouldBe true
    }
  }

}
