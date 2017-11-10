/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 22:14
 *
 */

package org.s4s0l.betelgeuse.utils.stomp

import org.scalatest.{FeatureSpec, FunSuite}

/**
  * @author Marcin Wielgus
  */
class SocksParserTest extends FeatureSpec {
  val expected: String =
    """SEND
      |someHeader:v"al
      |destination:/ala/ma/kota
      |content-length:42
      |
      |"{\"asdasdad\":\"asdasdasd\nasdasda,sd\"}"^@""".stripMargin

  feature("Can unscrumble socks message") {
    scenario("fucked up string") {
      assert(SocksParser.fromIncomingMessage("[\"SEND\\nsomeHeader:v\\\"al\\ndestination:/ala/ma/kota\\ncontent-length:42\\n\\n\\\"{\\\\\\\"asdasdad\\\\\\\":\\\\\\\"asdasdasd\\\\nasdasda,sd\\\\\\\"}\\\"\\u0000\"," +
        "\"SEND\\nsomeHeader:v\\\"al\\ndestination:/ala/ma/kota\\ncontent-length:42\\n\\n\\\"{\\\\\\\"asdasdad\\\\\\\":\\\\\\\"asdasdasd\\\\nasdasda,sd\\\\\\\"}\\\"\\u0000\"]") ==
        Seq(expected, expected))
    }
  }

}
