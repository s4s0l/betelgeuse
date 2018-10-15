package org.s4s0l.betelgeuse.akkaauth.manager

import org.s4s0l.betelgeuse.akkaauth.manager.HashProvider.HashedValue

/**
  * beware of timing attacks when implementing password verifications
  * for more details go to
  *
  * https://security.stackexchange.com/questions/83660/simple-string-comparisons-not-secure-against-timing-attacks
  */
trait HashProvider {
  def hashPassword(password: String): HashedValue

  def checkPassword(hash: HashedValue, password: String): Boolean
}

object HashProvider{
  case class HashedValue(hash: Array[Byte], salt:  Array[Byte])
}
