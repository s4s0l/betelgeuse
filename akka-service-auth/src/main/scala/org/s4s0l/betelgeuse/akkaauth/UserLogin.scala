package org.s4s0l.betelgeuse.akkaauth

import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

/**
  * @author Marcin Wielgus
  */
case class UserLogin(login: String, password: String) extends JacksonJsonSerializable {

}
