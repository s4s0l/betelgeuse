/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.utils

import java.net.{JarURLConnection, URL, URLClassLoader}
import java.util.jar.Manifest

/**
  * @author Marcin Wielgus
  */
object VersionUtils {

  case class Versions(
                       vcs: String,
                       name: String,
                       implementation: String,
                       specification: String,
                       manifest: String,
                       gradle: String,
                       buildJdk: String
                     )

  private val unknown = "<unknown>"
  private val unknownVersions = Versions(unknown, unknown, unknown, unknown,
    unknown, unknown, unknown)

  def getVersions(forCLass: Class[_]): Versions = {
    getManifestFromJar(forCLass)
      .orElse(getManifest(forCLass)).map { m =>
      Versions(
        Option(m.getMainAttributes.getValue("Source-Version")).getOrElse(unknown),
        Option(m.getMainAttributes.getValue("Implementation-Title")).getOrElse(unknown),
        Option(m.getMainAttributes.getValue("Implementation-Version")).getOrElse(unknown),
        Option(m.getMainAttributes.getValue("Specification-Version")).getOrElse(unknown),
        Option(m.getMainAttributes.getValue("Manifest-Version")).getOrElse(unknown),
        Option(m.getMainAttributes.getValue("Gradle-Version")).getOrElse(unknown),
        Option(m.getMainAttributes.getValue("Build-Jdk")).getOrElse(unknown)
      )
    }.getOrElse(unknownVersions)
  }

  def getManifest(forCLass: Class[_]): Option[Manifest] = {
    try {
      getClass.getClassLoader match {
        case cl: URLClassLoader =>
          Option(cl.findResource("META-INF/MANIFEST.MF"))
            .map(it => new Manifest(it.openStream()))
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

  def getManifestFromJar(forClass: Class[_]): Option[Manifest] = {
    try {
      val path = forClass.getProtectionDomain.getCodeSource.getLocation.getPath.replace("classes/", "")
      val url = new URL("jar:file:" + path + "test.jar!/")
      val jarConnection = url.openConnection.asInstanceOf[JarURLConnection]
      val manifest = jarConnection.getManifest
      Some(manifest)
    } catch {
      case _: Throwable =>
        None
    }
  }
}
