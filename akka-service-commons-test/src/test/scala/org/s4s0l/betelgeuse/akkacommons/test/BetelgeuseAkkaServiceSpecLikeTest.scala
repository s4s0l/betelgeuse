/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 14:45
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
