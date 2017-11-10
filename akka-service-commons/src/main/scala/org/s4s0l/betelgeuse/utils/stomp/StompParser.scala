/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 17:47
 *
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