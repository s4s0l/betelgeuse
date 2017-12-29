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

package org.s4s0l.betelgeuse.akkacommons

import java.lang.reflect.{Field, Modifier}
import java.net.URI
import java.util.UUID

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown, Props}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.utils.EventStreamListener

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.language.postfixOps

trait BgService {

  protected final lazy val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  lazy val serviceId: BgServiceId = BgServiceId(systemName, portBase)

  private var configInited = false
  private var mainRunned = false
  private val classNameMatch = "[^A-Z]+([A-Z][^$\\.]+)(?:.*)".r

  protected lazy val defaultSystemName: String = {
    this.getClass.getName match {
      case classNameMatch(name) => name
      case _ =>
        LOGGER.warn("Consider overriding systemName method as your class name is funny...")
        getClass.getSimpleName.replace("$", "")
    }
  }

  protected def systemName: String = defaultSystemName

  protected def portBase: Int = 1

  protected lazy val serviceInfo: ServiceInfo = new ServiceInfo(
    serviceId,
    getSystemProperty(s"${BgServiceExtension.configBaseKey}.instance", "1").toInt,
    getSystemProperty(s"${BgServiceExtension.configBaseKey}.docker", "false").toBoolean
  )

  protected def getSystemProperty(key: String, default: String = null): String =
    System.getProperty(key, default)
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgService])

  def customizeConfiguration: Config = {
    val sysName =
      s"""
         |${BgServiceExtension.configBaseKey}.name = $systemName
         |${BgServiceExtension.configBaseKey}.portBase = $portBase
         |${BgServiceExtension.configBaseKey}.docker = ${serviceInfo.docker}
         |${BgServiceExtension.configBaseKey}.instance = ${serviceInfo.instance}
         |${BgServiceExtension.configBaseKey}.portSuffix = ${serviceInfo.portSuffix}
         |${BgServiceExtension.configBaseKey}.firstPortSuffix = ${serviceInfo.firstPortSuffix}
         |${BgServiceExtension.configBaseKey}.bindAddress = ${serviceInfo.bindAddress}
         |${BgServiceExtension.configBaseKey}.externalAddress = ${serviceInfo.externalAddress}
         |""".stripMargin
    LOGGER.info(s"Customize config with: \n$sysName\n and fallback to service.conf")
    ConfigFactory.parseString(sysName).withFallback(ConfigFactory.parseResources("service.conf"))
  }

  def configFile: String = s"$systemName.conf"

  final implicit lazy val config: Config = {
    ConfigFactory.load(ConfigFactory.parseResources(configFile).withFallback(customizeConfiguration))
  }

  final implicit lazy val system: ActorSystem = {
    configInited = true
    if (!mainRunned) {
      log.warn("Use lazy vals!!!!, otherwise some fixes may not apply!")
    }
    ActorSystem(systemName, config)
  }

  implicit lazy val executor: ExecutionContextExecutor = system.dispatcher

  implicit lazy val materializer: Materializer = ActorMaterializer()

  def serviceExtension: BgServiceExtension = BgServiceExtension.get(system)

  def shutdownCoordinated(phase: String, taskName: String)(task: () ⇒ Future[Done]): Unit = {
    CoordinatedShutdown(system).addTask(phase, taskName)(task)
  }

  protected def initialize(): Unit = {
    LOGGER.info("Initializing...")
    shutdownCoordinated(
      "akka-service-shutdown", "akka-service-default-shutdowner") { () =>
      Future {
        beforeShutdown()
        Done
      }
    }
    system.registerExtension(BgServiceExtension)
    val listener = system.actorOf(Props[EventStreamListener], "eventStreamListener")
    system.eventStream.subscribe(listener, classOf[Any])
    LOGGER.info("Initializing done.")
  }

  protected def beforeShutdown(): Unit = {
    LOGGER.info("Before shutdown called")
  }

  def shutdown(): Unit = {
    LOGGER.info("Ensuring coordinated shutdown is triggered...")
    Await.result(CoordinatedShutdown(system).run(), 180 seconds)
    Await.result(system.whenTerminated, 180 seconds)
    LOGGER.info("Actor system terminated.")
  }

  private def startUpProcedure(): Unit = {
    LOGGER.info("Starting startup sequence...")
    val lock = registerOnInitializedBlocker()
    try {
      initialize()
    } catch {
      case e: Throwable =>
        log.error("Unable to initialize", e)
        shutdown()
    }

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        LOGGER.info("Shutdown hook triggered.")
        shutdown()
        LOGGER.info("Shutdown hook done.")
      }
    })
    unregisterOnInitializedBlocker(lock)
    LOGGER.info("Starting startup sequence completed.")
  }

  private val initializedBlockers = mutable.Set[String]()

  protected final def registerOnInitializedBlocker(): AnyRef = {
    initializedBlockers.synchronized {
      val ret = UUID.randomUUID().toString
      initializedBlockers += ret
      ret
    }
  }

  protected final def unregisterOnInitializedBlocker(lock: AnyRef): Unit = {
    initializedBlockers.synchronized {
      initializedBlockers -= lock.asInstanceOf[String]
      if (initializedBlockers.isEmpty) {
        LOGGER.info("Running onInitialized...")
        onInitialized()
        LOGGER.info("Completed onInitialized. Application should be fully running.")
      }
    }
  }

  protected def onInitialized(): Unit = {

  }

  final def run(): ActorSystem = {
    mainRunned = true
    if (configInited) {
      log.warn("Use lazy vals!!!, otherwise some fixes may not apply!")
    }
    fixUriBug()
    startUpProcedure()
    system
  }

  final def main(args: Array[String]): Unit = {
    run()
  }

  private def fixUriBug(): Unit = {
    val testString = "akka.tcp://toktme-story-handler@toktme-story_handlerservice:40000/system/receptionist"
    if (new URI(testString).getUserInfo == null) {
      LOGGER.info("Fixing URI bug (https://bugs.openjdk.java.net/browse/JDK-8170265)")
      //      see https://stackoverflow.com/questions/28568188/java-net-uri-get-host-with-underscores
      patchUriField("lowMask", "L_DASH")
      patchUriField("highMask", "H_DASH")
      if (new URI(testString).getUserInfo == null) {
        throw new RuntimeException("Unable to fix uris!")
      }
    }
  }

  import java.lang.reflect.InvocationTargetException

  @throws[NoSuchMethodException]
  @throws[IllegalAccessException]
  @throws[InvocationTargetException]
  @throws[NoSuchFieldException]
  private def patchUriField(methodName: String, fieldName: String): Unit = {
    val lowMask = classOf[URI].getDeclaredMethod(methodName, classOf[String])
    lowMask.setAccessible(true)
    val lowMaskValue = lowMask.invoke(null, "-_").asInstanceOf[Long]
    val lowDash = classOf[URI].getDeclaredField(fieldName)
    val modifiers = classOf[Field].getDeclaredField("modifiers")
    modifiers.setAccessible(true)
    modifiers.setInt(lowDash, lowDash.getModifiers & ~Modifier.FINAL)
    lowDash.setAccessible(true)
    lowDash.setLong(null, lowMaskValue)
  }

}
