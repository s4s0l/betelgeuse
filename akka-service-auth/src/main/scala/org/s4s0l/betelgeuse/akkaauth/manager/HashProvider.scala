package org.s4s0l.betelgeuse.akkaauth.manager

trait HashProvider {
  def hashPassword(password: String): String

  def checkPassword(hash: String, password: String): Boolean
}
