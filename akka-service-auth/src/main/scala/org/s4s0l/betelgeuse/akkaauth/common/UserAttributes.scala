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

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import org.s4s0l.betelgeuse.akkaauth.common.UserAttributes._

/**
  * @author Marcin Wielgus
  */
case class UserAttributes(
                           given_name: Option[String] = None,
                           family_name: Option[String] = None,
                           middle_name: Option[String] = None,
                           nickname: Option[String] = None,
                           preferred_username: Option[String] = None,
                           profile: Option[URI] = None,
                           picture: Option[URI] = None,
                           website: Option[URI] = None,
                           email: Option[Email] = None,
                           gender: Option[Gender] = None,
                           birthDate: Option[Date] = None,
                           zoneInfo: Option[TimeZone] = None,
                           locale: Option[Locale] = None,
                           phone_number: Option[PhoneNumber] = None,
                           address: Option[String] = None,
                           country: Option[Country] = None,
                           city: Option[City] = None
                         )

object UserAttributes {

  case class Email(address: String)

  case class City(name: String)

  case class Country(address: String)

  case class PhoneNumber(number: String)

  @JsonInclude(Include.NON_NULL)
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes(Array(
    new Type(name = "male", value = classOf[Male]),
    new Type(name = "female", value = classOf[Female]),
    new Type(name = "other", value = classOf[Other])
  ))
  sealed trait Gender

  case class Male() extends Gender

  case class Female() extends Gender

  case class Other(name: String) extends Gender


}
