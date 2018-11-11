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

package org.s4s0l.betelgeuse.akkaauth.audit

import java.util.Date

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import org.s4s0l.betelgeuse.akkaauth.audit.StreamingAuditDto._

/**
  * @author Marcin Wielgus
  */
@JsonInclude(Include.NON_NULL)
@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "eventKind")
@JsonSubTypes(Array(
  new Type(name = "client", value = classOf[AuthClientEventDto]),
  new Type(name = "provider", value = classOf[AuthProviderEventDto])
))
sealed trait StreamingAuditDto {
  def id: String

  def serviceInfo: ServiceInfo

  def routeInfo: RouteInfo

  def eventType: String

  def timestamp: Date

  def authInfo: Option[AuthInfoDto]

  def errorMessage: Option[String]
}

object StreamingAuditDto {


  case class AuthClientEventDto(id: String,
                                serviceInfo: ServiceInfo,
                                routeInfo: RouteInfo,
                                eventType: String,
                                authInfo: Option[AuthInfoDto] = None,
                                missingGrants: List[String] = List(),
                                errorMessage: Option[String] = None,
                                timestamp: Date = new Date()
                               )
    extends StreamingAuditDto

  case class AuthProviderEventDto(id: String,
                                  serviceInfo: ServiceInfo,
                                  routeInfo: RouteInfo,
                                  eventType: String,
                                  authInfo: Option[AuthInfoDto] = None,
                                  inBehalfOfUserId: Option[String] = None,
                                  tokenId: Option[String] = None,
                                  errorMessage: Option[String] = None,
                                  timestamp: Date = new Date()
                                 )
    extends StreamingAuditDto

  case class AuthInfoDto(
                          tokenId: String,
                          tokenTypeName: String,
                          login: Option[String],
                          userId: String,
                          attributes: Map[String, String]
                        )

  case class ServiceInfo(bgServiceId: String,
                         bgInstanceId: String)

  case class RouteInfo(ip: String,
                       method: String,
                       uri: String,
                       path: String)


}
