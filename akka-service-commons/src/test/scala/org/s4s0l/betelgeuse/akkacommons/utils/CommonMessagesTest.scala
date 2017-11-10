/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-05 22:26
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import org.s4s0l.betelgeuse.akkacommons.utils.CommonMessages.Headers
import org.scalatest.{FeatureSpec, FunSuite}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class CommonMessagesTest extends FeatureSpec {

  feature("Messages can have ttl") {
    scenario("Setting and getting ttl") {
      val x = Headers().withTtl(1 second)
      assert(x.ttlOpt.isDefined)
      assert(!x.ttl.isOverdue())
      Thread.sleep(1010)
      assert(x.ttl.isOverdue())
    }
  }

}
