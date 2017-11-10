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

import org.scalatest.FeatureSpec

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
