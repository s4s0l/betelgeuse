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

package org.s4s0l.betelgeuse.akkacommons.test

import org.s4s0l.betelgeuse.akkacommons.{BetelgeuseAkkaService, BetelgeuseAkkaServiceExtension}

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaServiceSpecLikeTest extends BetelgeuseAkkaServiceSpecLike[BetelgeuseAkkaService] {
  override def createService(): BetelgeuseAkkaService = new BetelgeuseAkkaService() {}

  feature("bg.info config path is filled") {
    scenario("no parameters start") {
      val ext = BetelgeuseAkkaServiceExtension(system).serviceInfo

      assert(system.settings.config.getString("bg.info.name") == ext.serviceName)
      assert(system.settings.config.getInt("bg.info.portBase") == ext.portBase)
      assert(system.settings.config.getBoolean("bg.info.docker") == ext.docker)
      assert(system.settings.config.getInt("bg.info.instance") == ext.instance)
      assert(system.settings.config.getString("bg.info.portSuffix") == ext.portSuffix)
      assert(system.settings.config.getString("bg.info.firstPortSuffix") == ext.firstPortSuffix)
      assert(system.settings.config.getString("bg.info.bindAddress") == ext.bindAddress)
      assert(system.settings.config.getString("bg.info.externalAddress") == ext.externalAddress)
    }
  }

}
