/*
 * Copyright© 2017 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.test

import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.s4s0l.betelgeuse.akkacommons.{BgService, BgServiceExtension, ServiceInfo}

/**
  * @author Marcin Wielgus
  */
class BgTestServiceTest extends BgTestService {

  private val serviceOne = testWith(new BgService() {})
  private val serviceTwo = testWith(new BgService() {

    override def systemName: String = "BgTestServiceTest2"

    override def portBase = 2
  })


  feature("BgService testing is possible") {
    scenario("Just some scenario using implicit stuff") {
      new WithService(serviceOne) {
        val ext: ServiceInfo = BgServiceExtension(system).serviceInfo
        assert(ext.id.systemName == "BgTestServiceTest")
      }
    }

    scenario("Just some scenario using both services") {
      val ext = BgServiceExtension(serviceOne.system).serviceInfo
      assert(ext.id.systemName == "BgTestServiceTest")
      val ext2 = BgServiceExtension(serviceTwo.system).serviceInfo
      assert(ext2.id.systemName == "BgTestServiceTest2")
      assert(serviceOne.system.settings.config.getString("bg.info.name") == ext.id.systemName)
      assert(serviceOne.system.settings.config.getInt("bg.info.portBase") == ext.id.portBase)
      assert(serviceOne.system.settings.config.getBoolean("bg.info.docker") == ext.docker)
      assert(serviceOne.system.settings.config.getInt("bg.info.instance") == ext.instance)
      assert(serviceOne.system.settings.config.getString("bg.info.portSuffix") == ext.portSuffix)
      assert(serviceOne.system.settings.config.getString("bg.info.firstPortSuffix") == ext.firstPortSuffix)
      assert(serviceOne.system.settings.config.getString("bg.info.bindAddress") == ext.bindAddress)
      assert(serviceOne.system.settings.config.getString("bg.info.externalAddress") == ext.externalAddress)
    }
  }

}
