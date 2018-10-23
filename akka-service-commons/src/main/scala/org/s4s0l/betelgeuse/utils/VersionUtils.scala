
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
      val url = new URL("jar:file:" + path + "!/")
      val jarConnection = url.openConnection.asInstanceOf[JarURLConnection]
      val manifest = jarConnection.getManifest
      Some(manifest)
    } catch {
      case _: Throwable =>
        None
    }
  }
}
