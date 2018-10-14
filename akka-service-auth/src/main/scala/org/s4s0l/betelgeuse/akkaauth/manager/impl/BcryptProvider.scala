package org.s4s0l.betelgeuse.akkaauth.manager.impl

import org.bouncycastle.crypto.generators.SCrypt
import org.mindrot.jbcrypt.BCrypt

/** Provides java Bcrypt hashing of strings
  *
  * https://en.wikipedia.org/wiki/Bcrypt
  *
  * Awesome so answer https://security.stackexchange.com/questions/17421/how-to-store-salt
  *
  * @param logRounds log2 of the number of rounds of hashing to apply
  *                  the work factor therefore increases as
  *                  2**log_rounds.
  */
class BcryptProvider(logRounds: Int) extends HashProvider {

  override def hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(logRounds))

  override def checkPassword(hash: String, password: String): Boolean = BCrypt.checkpw(password, hash)
}
