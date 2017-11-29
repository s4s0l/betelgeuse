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

package org.s4s0l.betelgeuse.akkacommons

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.collection.mutable

/**
  * @author Marcin Wielgus
  */
class BgServiceExtension(private val system: ExtendedActorSystem) extends Extension {

  private val plugins = mutable.Map[Class[_], Seq[() => AnyRef]]()

  def serviceInfo: ServiceInfo = {
    new ServiceInfo(
      BgServiceId(
        system.settings.config.string(s"${BgServiceExtension.configBaseKey}.name").get,
        system.settings.config.int(s"${BgServiceExtension.configBaseKey}.portBase").get
      ),
      system.settings.config.int(s"${BgServiceExtension.configBaseKey}.instance").get,
      system.settings.config.boolean(s"${BgServiceExtension.configBaseKey}.docker").get
    )
  }

  def pluginRegister[T <: AnyRef](iface: Class[T], provider: () => T): Unit = {
    plugins.synchronized {
      val x = plugins.getOrElse(iface, Seq())
      plugins(iface) = provider +: x
    }
  }

  def pluginGet[T <: AnyRef](iface: Class[T]): T = {
    plugins.synchronized {
      plugins(iface).head.apply().asInstanceOf[T]
    }
  }

  def pluginGetAll[T <: AnyRef](iface: Class[T]): Seq[T] = {
    plugins.synchronized {
      plugins.getOrElse(iface, Seq()).map {
        it => it.apply().asInstanceOf[T]
      }
    }
  }

}

object BgServiceExtension extends ExtensionId[BgServiceExtension] with ExtensionIdProvider {
  val configBaseKey: String = "bg.info"

  override def apply(system: ActorSystem): BgServiceExtension = system.extension(this)

  override def get(system: ActorSystem): BgServiceExtension = system.extension(this)

  override def lookup(): BgServiceExtension.type = BgServiceExtension

  override def createExtension(system: ExtendedActorSystem): BgServiceExtension =
    new BgServiceExtension(system)


}


