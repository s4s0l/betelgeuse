
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

package org.s4s0l.betelgeuse.akkacommons

/**
  * @author Marcin Wielgus
  */
class ServiceInfo(val id: BgServiceId,
                  val instance: Int = System.getProperty(s"${BgServiceExtension.configBaseKey}.instance", "1").toInt,
                  val docker: Boolean = System.getProperty(s"${BgServiceExtension.configBaseKey}.docker", "false").toBoolean) {

  val portSuffix: String = if (docker) firstPortSuffix else "%02d%02d".format(id.portBase, instance)
  val externalAddress: String = if (docker) s"${id.systemName}_service" else "127.0.0.1"

  def firstPort(portType: Int): Int = "%d%02d%02d".format(portType, id.portBase, instance).toInt

  val bindAddress: String = "0.0.0.0"

  def firstPortSuffix: String = "%02d%02d".format(id.portBase, 1)

}
