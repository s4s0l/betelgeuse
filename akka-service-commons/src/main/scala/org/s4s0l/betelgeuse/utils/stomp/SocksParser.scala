/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 21:50
 *
 */

package org.s4s0l.betelgeuse.utils.stomp


import fastparse.all._

/**
  * ["SEND\nsomeHeader:v\"al\ndestination:/ala/ma/kota\ncontent-length:42\n\n\"{\\\"asdasdad\\\":\\\"asdasdasd\\nasdasda,sd\\\"}\"\u0000"]
  *
  * @author Marcin Wielgus
  */
object SocksParser {

  private case class NamedFunction[T, V](f: T => V, name: String) extends (T => V) {
    def apply(t: T) = f(t)

    override def toString(): String = name

  }

  private val tmpNewLine = "_NEW_LINE_FUCKING_MARKER_"
  private val space = P(CharsWhileIn(" \r\n").?)
  private val StringChars = NamedFunction(!"\"\\".contains(_: Char), "StringChars")
  private val hexDigit = P(CharIn('0' to '9', 'a' to 'f', 'A' to 'F'))
  private val strChars = P(CharsWhile(StringChars))
  private val unicodeEscape = P("u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit)
  private val escape = P("\\" ~ (CharIn("\"/\\bfnrt") | unicodeEscape))
  private val string =
    P(space ~ "\"" ~/ (strChars | escape).rep.! ~ "\"")

  //  val singleCommand = CharsWhile(_ != '"').!
  private val parser = "[" ~ string.rep(sep = P(space ~ "," ~ space)) ~ "]"

  def fromIncomingMessage(msg: String): Seq[String] = {
    parser.parse(msg) match {
      case Parsed.Success(a, _) =>
        a.map(unescape)
      case f: Parsed.Failure =>
        throw new RuntimeException(f.toString())
    }

  }

  private def unescape(msg: String): String = msg
    .replace("\\\\n", tmpNewLine)
    .replace("\\u0000", "^@")
    .replace("\\\\", "\\")
    .replace("\\\"", "\"")
    .replace("\\n", "\n")
    .replace(tmpNewLine, "\\n")

  private def escape(msg: String): String = msg
    .replace("\\", "\\\\")
    .replace("^@", "\\u0000")
    .replace("\n", "\\n")
    .replace("\"", "\\\"")

  def toOutgoingMessage(msg: Seq[String]): String = {
    "a[" + msg.map(escape(_)).map("\"" + _ + "\"").mkString(",") + "]"
  }


}
