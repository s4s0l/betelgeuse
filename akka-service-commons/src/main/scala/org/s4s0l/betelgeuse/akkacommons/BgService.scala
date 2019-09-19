/*
 * Copyright© 2019 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

import java.io.File
import java.lang.reflect.{Field, Modifier}
import java.net.URI

import akka.Done
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.actor.{ActorSystem, CoordinatedShutdown, Props, Scheduler}
import akka.serialization.{Serialization, SerializationExtension}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.serialization.{HttpMarshalling, JacksonJsonSerializer}
import org.s4s0l.betelgeuse.akkacommons.utils.EventStreamListener
import org.s4s0l.betelgeuse.utils.UuidUtils

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.language.postfixOps

trait BgService
  extends BgVersionProvider {

  protected final lazy val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  lazy val serviceId: BgServiceId = BgServiceId(systemName, portBase)

  private var configInited = false
  private var mainRunned = false
  private val classNameMatch = "[^A-Z]+([A-Z][^$.]+)(?:.*)".r

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

  protected lazy val serviceInfo: ServiceInfo = new ServiceInfo(serviceId)

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
    ConfigFactory.parseString(sysName)
      .withFallback(ConfigFactory.parseResources("service.conf"))
      .withFallback(ConfigFactory.parseResources("serialization-jackson.conf"))
  }

  def externalFileConfiguration: Config = {
    val configsPaths = System.getProperty("bg.configs")
    if (configsPaths != null && configsPaths.trim != "") {
      LOGGER.info(s"Picked up bg.configs = $configsPaths")
      configsPaths
        .split(s"[,${File.pathSeparator}]+")
        .reverse
        .map { it =>
          val f = new File(it)
          if (f.exists() && f.canRead) {
            LOGGER.info(s"Going to use ${f.getAbsolutePath} as config file")
            ConfigFactory.parseFile(f)
          } else {
            throw new Exception(s"Config file ${f.getAbsolutePath} does not exist!")
          }
        }
        .foldLeft(ConfigFactory.empty()) { (c, n) =>
          c.withFallback(n)
        }
    } else {
      LOGGER.info(s"No external configs found")
      ConfigFactory.empty()
    }
  }

  def configFile: String = s"$systemName.conf"

  final implicit lazy val config: Config = {
    externalFileConfiguration.withFallback(
      ConfigFactory.load(
        ConfigFactory.parseResources(configFile)
          .withFallback(customizeConfiguration))
    ).resolve()
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

  implicit lazy val serializer: Serialization = SerializationExtension(system)

  implicit lazy val serializationJackson: JacksonJsonSerializer = new JacksonJsonSerializer()

  implicit lazy val httpMarshalling: HttpMarshalling = new HttpMarshalling(serializationJackson)

  implicit lazy val akkaSystemScheduler: Scheduler = system.scheduler

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
    Await.result(CoordinatedShutdown(system).run(UnknownReason), 180 seconds)
    Await.result(system.whenTerminated, 180 seconds)
    LOGGER.info("Actor system terminated.")
  }

  private def startUpProcedure(): Unit = {
    LOGGER.info("Starting startup sequence...")
    val _ = config
    LOGGER.info("Configuration prepared")
    val lock = registerOnInitializedBlocker()
    try {
      initialize()
    } catch {
      case e: Throwable =>
        log.error("Unable to initialize", e)
        shutdown()
        return
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
      val ret = UuidUtils.timeBasedUuid().toString
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

  def displayDiagnostics(): Unit = {
    LOGGER.info(s"Working dir is: [${new File(".").getAbsolutePath}]")
    LOGGER.info(s"java info: java.home=${System.getProperty("java.home")}")
    LOGGER.info(s"java info: java.version=${System.getProperty("java.version")}")
    LOGGER.info(s"java info: java.version.date=${System.getProperty("java.version.date")}")
    LOGGER.info(s"java info: java.vendor=${System.getProperty("java.vendor")}")
    LOGGER.info(s"java info: java.vendor.version=${System.getProperty("java.vendor.version")}")
    LOGGER.info(s"java info: java.vendor.url=${System.getProperty("java.vendor.url")}")
    LOGGER.info(s"java info: java.vendor.url.bug=${System.getProperty("java.vendor.url.bug")}")
    LOGGER.info(s"java info: java.specification.name=${System.getProperty("java.specification.name")}")
    LOGGER.info(s"java info: java.specification.vendor=${System.getProperty("java.specification.vendor")}")
    LOGGER.info(s"java info: java.specification.version=${System.getProperty("java.specification.version")}")
    LOGGER.info(s"java info: java.vm.name=${System.getProperty("java.vm.name")}")
    LOGGER.info(s"java info: java.vm.vendor=${System.getProperty("java.vm.vendor")}")
    LOGGER.info(s"java info: java.vm.version=${System.getProperty("java.vm.version")}")
    LOGGER.info(s"java info: java.vm.info=${System.getProperty("java.vm.info")}")
    LOGGER.info(s"java info: java.vm.specification.name=${System.getProperty("java.vm.specification.name")}")
    LOGGER.info(s"java info: java.vm.specification.vendor=${System.getProperty("java.vm.specification.vendor")}")
    LOGGER.info(s"java info: java.vm.specification.version=${System.getProperty("java.vm.specification.version")}")
    LOGGER.info(s"java info: java.runtime.name=${System.getProperty("java.runtime.name")}")
    LOGGER.info(s"java info: java.runtime.version=${System.getProperty("java.runtime.version")}")
    LOGGER.info(s"java info: java.class.version=${System.getProperty("java.class.version")}")
    LOGGER.info(s"java info: jdk.debug=${System.getProperty("jdk.debug")}")
    LOGGER.info(s"java info: sun.java.launcher=${System.getProperty("sun.java.launcher")}")
    LOGGER.info(s"java info: sun.management.compiler=${System.getProperty("sun.management.compiler")}")
  }

  final def run(): ActorSystem = {
    displayDiagnostics()
    val versions = getVersionsInfo
    log.info("Betelgeuse@{} is starting application {}@{} generated from {}",
      versions.bgVersions.implementation,
      versions.appVersions.name,
      versions.appVersions.implementation,
      versions.appVersions.vcs
    )
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

  protected def fixUriBug(): Unit = {
    val testString = "akka.tcp://toktme-story-handler@toktme-story_handlerservice:40000/system/bg_receptionist"
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
