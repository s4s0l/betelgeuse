/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */



package org.s4s0l.betelgeuse.utils.stomp

import fastparse.all._
import fastparse.core

/**
  * ["CONNECT\nUpgrade:websocket\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000"]
  *
  * @author Marcin Wielgus
  */
object StompParser {
  val empty: core.Parser[Unit, Char, String] = CharsWhile(_ == ' ').?
  val newLine: core.Parser[Unit, Char, String] = empty ~ "\n"
  val header: core.Parser[(String, String), Char, String] = !newLine ~ empty ~ CharsWhile(_ != ':').! ~ ":" ~ (!"\n" ~ AnyChar).rep.! ~ newLine
  val end: core.Parser[Unit, Char, String] = "^@" ~ End
  val body: core.Parser[Unit, Char, String] = (!end ~ AnyChar).rep
  val singleCommand: core.Parser[(String, Seq[(String, String)], String), Char, String] = Stomp.Commands.parser.! ~ newLine ~ header.rep ~ newLine ~ body.?.! ~ end


  def parse(wsMessage: String): StompMessage = {
    singleCommand.parse(wsMessage) match {
      case Parsed.Success(row, _) =>
        StompMessage(row._1, row._2.toMap, row._3 match { case "" => None; case a => Some(a) })
      case f: Parsed.Failure =>
        throw new RuntimeException(f.toString())
    }

  }

}