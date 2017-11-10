package org.s4s0l.betelgeuse.akkacommons

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.collection.mutable

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaServiceExtension(private val system: ExtendedActorSystem) extends Extension {

  private val plugins = mutable.Map[Class[_], Seq[() => AnyRef]]()

  def serviceInfo: ServiceInfo = {
    new ServiceInfo(
      system.settings.config.string(s"${BetelgeuseAkkaServiceExtension.configBaseKey}.name").get,
      system.settings.config.int(s"${BetelgeuseAkkaServiceExtension.configBaseKey}.instance").get,
      system.settings.config.int(s"${BetelgeuseAkkaServiceExtension.configBaseKey}.portBase").get,
      system.settings.config.boolean(s"${BetelgeuseAkkaServiceExtension.configBaseKey}.docker").get
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

object BetelgeuseAkkaServiceExtension extends ExtensionId[BetelgeuseAkkaServiceExtension] with ExtensionIdProvider {
  val configBaseKey: String = "bg.info"

  override def apply(system: ActorSystem): BetelgeuseAkkaServiceExtension = system.extension(this)

  override def get(system: ActorSystem): BetelgeuseAkkaServiceExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaServiceExtension.type = BetelgeuseAkkaServiceExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaServiceExtension =
    new BetelgeuseAkkaServiceExtension(system)


}


