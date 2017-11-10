/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 10:11
 */

package org.s4s0l.betelgeuse.akkacommons.http


trait BetelgeuseAkkaHttpSession extends BetelgeuseAkkaHttp {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BetelgeuseAkkaHttpSession])


  abstract protected override def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    system.registerExtension(BetelgeuseAkkaHttpSessionExtension)
    LOGGER.info("Initializing done.")
  }


  def httpSessionExtension: BetelgeuseAkkaHttpSessionExtension =
    BetelgeuseAkkaHttpSessionExtension.get(system)

}

