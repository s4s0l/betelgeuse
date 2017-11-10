/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 17:56
 *
 */

package org.s4s0l.betelgeuse.utils.stomp

import fastparse.all.P
import org.s4s0l.betelgeuse.utils.stomp.StompParser.{empty, newLine}
import org.scalatest.{FeatureSpec, FunSuite}

/**
  * @author Marcin Wielgus
  */
class StompParserTest extends FeatureSpec {

  feature("Parser can handle messages comming from web socket") {

    scenario("Connect message") {
      assert(parse("[\"CONNECT\\nUpgrade:websocket\\naccept-version:1.1,1.0\\nheart-beat:10000,10000\\n\\n\\u0000\"]") ==
        Seq(StompMessage(Stomp.Commands.CONNECT, Map(
          Stomp.Headers.Connect.ACCEPT_VERSION -> "1.1,1.0",
          "Upgrade" -> "websocket",
          Stomp.Headers.Connect.HEART_BEAT -> "10000,10000"),
          None)
        ))
    }


    scenario("Two messages ") {
      assert(parse(
        "[" +
          "\"CONNECT\\nUpgrade:websocket\\naccept-version:1.1,1.0\\nheart-beat:10000,10000\\n\\n\\u0000\"" +
          "," +
          "\"CONNECT\\nUpgrade:websocket2\\naccept-version:1.1,1.0\\nheart-beat:10000,10000\\n\\n\\u0000\"" +
          "]"
      ) ==
        Seq(
          StompMessage(Stomp.Commands.CONNECT, Map(
            Stomp.Headers.Connect.ACCEPT_VERSION -> "1.1,1.0",
            "Upgrade" -> "websocket",
            Stomp.Headers.Connect.HEART_BEAT -> "10000,10000"),
            None),
          StompMessage(Stomp.Commands.CONNECT, Map(
            Stomp.Headers.Connect.ACCEPT_VERSION -> "1.1,1.0",
            "Upgrade" -> "websocket2",
            Stomp.Headers.Connect.HEART_BEAT -> "10000,10000"),
            None)
        ))

    }

    scenario("Connect message with body") {
      assert(parse("[\"CONNECT\\nUpgrade:websocket\\naccept-version:1.1,1.0\\nheart-beat:10000,10000\\n\\nAAA\\u0000\"]") ==
        Seq(StompMessage(Stomp.Commands.CONNECT, Map(
          Stomp.Headers.Connect.ACCEPT_VERSION -> "1.1,1.0",
          "Upgrade" -> "websocket",
          Stomp.Headers.Connect.HEART_BEAT -> "10000,10000"),
          Some("AAA"))
        ))
    }


    scenario("Subs message") {
      assert(parse("[\"SUBSCRIBE\\nid:sub-0\\ndestination:/topic/greetings\\n\\n\\u0000\"]") ==
        Seq(StompMessage(Stomp.Commands.SUBSCRIBE, Map(
          Stomp.Headers.Subscribe.DESTINATION -> "/topic/greetings",
          "id" -> "sub-0"
        ),
          None)
        ))
    }
  }

  def parse(s: String): Seq[StompMessage] = {
    SocksParser.fromIncomingMessage(s).map(StompParser.parse)
  }

}
