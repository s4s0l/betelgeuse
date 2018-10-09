package org.s4s0l.betelgeuse.akkaauth

import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

/**
  * @author Marcin Wielgus
  */
case class AuthenticationInfo(
                               jwtId: String,
                               login: String,
                               roles: List[String],
                               expiration: Long,
                               attributes: Map[String, String]
                             )
  extends JacksonJsonSerializable {

}
