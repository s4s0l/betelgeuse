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



package org.s4s0l.betelgeuse.utils.stomp

/**
  * @author Marcin Wielgus
  */
case class StompMessage(command: String, headers: Map[String, String], payload: Option[String]) {


  def toPayload: String = {
    val newLine: String = "\n"
    val hdrs = headers.map(it => it._1 + ":" + it._2).mkString(newLine)
    val pld = payload.map(x => x).getOrElse("")
    command + newLine + hdrs + newLine+ newLine + pld + "^@"
  }

}
