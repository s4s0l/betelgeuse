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

import java.net.URI
import java.util.{Date, Locale, TimeZone}

import org.s4s0l.betelgeuse.akkaauth.common.UserAttributes._

/**
  * @author Marcin Wielgus
  */
case class UserAttributes(
                           given_name: Option[String],
                           family_name: Option[String],
                           middle_name: Option[String],
                           nickname: Option[String],
                           preferred_username: Option[String],
                           profile: Option[URI],
                           picture: Option[URI],
                           website: Option[URI],
                           email: Option[Email],
                           gender: Option[Gender],
                           birthDate: Option[Date],
                           zoneInfo: Option[TimeZone],
                           locale: Option[Locale],
                           phone_number: Option[PhoneNumber],
                           address: Option[String],
                           country: Option[Country],
                           city: Option[City]
                         )

object UserAttributes {

  case class Email(address: String)

  case class City(name: String)

  case class Country(address: String)

  case class PhoneNumber(number: String)

  sealed trait Gender

  case object Male extends Gender

  case object Female extends Gender

  case class Other(name: String) extends Gender


}
