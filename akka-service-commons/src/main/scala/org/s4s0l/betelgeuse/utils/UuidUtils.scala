/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.utils

import com.fasterxml.uuid.{EthernetAddress, Generators}

/**
  * @author Marcin Wielgus
  */
object UuidUtils {
  private lazy val timeBasedGenerator = Generators.timeBasedGenerator(EthernetAddress.fromInterface())

  def timeBasedUuid() = {
    timeBasedGenerator.generate()
  }
}
