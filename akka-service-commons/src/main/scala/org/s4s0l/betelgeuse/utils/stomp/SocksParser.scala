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
