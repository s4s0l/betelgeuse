package org.s4s0l.betelgeuse.akkacommons

/**
  * @author Marcin Wielgus
  */
class ServiceInfo(val serviceName: String, val instance: Int, val portBase: Int, val docker: Boolean) {

  def firstPort(portType: Int): Int = "%d%02d%02d".format(portType, portBase, instance).toInt

  def firstPortSuffix: String = "%02d%02d".format(portBase, 1)

  val portSuffix: String = if (docker) firstPortSuffix else "%02d%02d".format(portBase, instance)

  val bindAddress: String = "0.0.0.0"

  val externalAddress: String = if(docker) s"${serviceName}_service" else "127.0.0.1"

}
