/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 17:57
 *
 */

package org.s4s0l.betelgeuse.utils.stomp

import scala.collection.mutable

/**
  * @author Marcin Wielgus
  */
case class StompMessage(command: String, headers: Map[String, String], payload: Option[String]) {


  def toPayload: String = {
    val newLine: String = "\n"
    val hdrs = headers.map(it => it._1 + ":" + it._2).mkString(newLine)
    val pld = payload.map(x => x).getOrElse("")
    command + newLine + hdrs + newLine+ newLine + pld + "^@"
  }

}
