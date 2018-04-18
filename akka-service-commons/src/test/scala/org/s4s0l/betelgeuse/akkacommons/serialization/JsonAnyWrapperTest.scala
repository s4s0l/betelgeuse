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

package org.s4s0l.betelgeuse.akkacommons.serialization

import org.s4s0l.betelgeuse.akkacommons.serialization.JsonAnyWrapperTest.{Class1, Class2}
import org.scalatest.FeatureSpec

/**
  * @author Marcin Wielgus
  */
class JsonAnyWrapperTest extends FeatureSpec {

  feature("We have a convinient way to store objects of unknown type as jsons") {
    scenario("Jackson lack of ofeatureaware wrapper does the job") {
      val s = new JacksonJsonSerializer();
      {
        val bin = s.toBinary(Class1(None))
        assert(new String(bin) == """{"x":{"wrapped":{},"@class":"org.s4s0l.betelgeuse.akkacommons.serialization.JsonAnyWrapper$NullWrapper"}}""")
        val value: Option[String] = s.fromBinary(bin, classOf[Class1]).asInstanceOf[Class1].x
        assert(value.isEmpty)
      }
      {
        val bin = s.toBinary(Class1(Some("Hello!")))
        assert(new String(bin) == """{"x":{"wrapped":{"stringValue":"Hello!"},"@class":"org.s4s0l.betelgeuse.akkacommons.serialization.JsonAnyWrapper$StringWrapper"}}""")
        val value: Option[String] = s.fromBinary(bin, classOf[Class1]).asInstanceOf[Class1].x
        assert(value.contains("Hello!"))
      }
      {
        val bin = s.toBinary(Class1(Some(Class2(1))))
        assert(new String(bin) == """{"x":{"wrapped":{"v":1},"@class":"org.s4s0l.betelgeuse.akkacommons.serialization.JsonAnyWrapperTest$Class2"}}""")
        val value: Option[Class2] = s.fromBinary(bin, classOf[Class1]).asInstanceOf[Class1].x
        assert(value.contains(Class2(1)))
      }
    }
  }

}

object JsonAnyWrapperTest {

  case class Class1(x: JsonAnyWrapper)

  case class Class2(v: Int) extends JacksonJsonSerializable


}
