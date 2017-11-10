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

package org.s4s0l.betelgeuse.utils

import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.reflect.runtime.universe

/**
  * @author Marcin Wielgus
  */
trait CompanionFinder {


  def scalaWarmUpUniverseMirror(cl: ClassLoader = getClass.getClassLoader): Unit = {
    val start = System.currentTimeMillis()
    universe.runtimeMirror(cl)
    LOGGER.info(s"Getting runtime mirror ${System.currentTimeMillis() - start} ms")
  }


  def findCompanionForObject[T, C](of: T): C = {
    //https://stackoverflow.com/questions/11020746/get-companion-object-instance-with-new-scala-reflection-api
    //and potencially https://stackoverflow.com/questions/16440124/get-the-companion-object-instance-of-a-inner-modul-with-the-scala-reflection-api

    //version A

    //    import scala.reflect.runtime._
    //    val theClass = of.getClass
    //    val rootMirror = universe.runtimeMirror(theClass.getClassLoader)
    //    val classSymbol = rootMirror.classSymbol(theClass)
    //    val moduleSymbol = classSymbol.companion.asModule
    //    val moduleMirror = rootMirror.reflectModule(moduleSymbol)
    //    moduleMirror.instance.asInstanceOf[CrateDbObjectMapper[T]]

    //VERSION B
    import scala.reflect.runtime.{currentMirror => cm}
    val outerField = of.getClass.getFields.find(_.getName == """$outer""")
    val moduleMirror = outerField.map {
      _.get(of)
    }.map {
      cm.reflect(_)
    }
    val instanceSymbol = cm.classSymbol(of.getClass)
    val companionSymbol = instanceSymbol.companion.asModule
    val companionMirror = moduleMirror.map {
      _.reflectModule(companionSymbol)
    }.
      getOrElse {
        cm.reflectModule(companionSymbol)
      }
    companionMirror.instance.asInstanceOf[C]
  }

  def findCompanionForClass[C](theClass: Class[_]): C = {
    //https://stackoverflow.com/questions/11020746/get-companion-object-instance-with-new-scala-reflection-api
    //and potencially https://stackoverflow.com/questions/16440124/get-the-companion-object-instance-of-a-inner-modul-with-the-scala-reflection-api
    import scala.reflect.runtime._
    val rootMirror = universe.runtimeMirror(theClass.getClassLoader)
    val classSymbol = rootMirror.classSymbol(theClass)
    val moduleSymbol = classSymbol.companion.asModule
    val moduleMirror = rootMirror.reflectModule(moduleSymbol)
    moduleMirror.instance.asInstanceOf[C]

  }

  def findCompanionForClassOption[C](theClass: Class[_])(implicit classTag: ClassTag[C]): Option[C] = {
    //https://stackoverflow.com/questions/11020746/get-companion-object-instance-with-new-scala-reflection-api
    //and potencially https://stackoverflow.com/questions/16440124/get-the-companion-object-instance-of-a-inner-modul-with-the-scala-reflection-api
    import scala.reflect.runtime._
    val rootMirror = universe.runtimeMirror(theClass.getClassLoader)
    val classSymbol = rootMirror.classSymbol(theClass)
    val moduleSymbol = classSymbol.companion.asModule
    val moduleMirror = rootMirror.reflectModule(moduleSymbol)
    try {
      val instance = moduleMirror.instance
      if (!classTag.runtimeClass.isAssignableFrom(instance.getClass)) {
        None
      } else {
        Option(instance.asInstanceOf[C])
      }
    } catch {
      case ex: ClassNotFoundException =>
        LOGGER.warn(s"Do not call findCompanionForClassOption for classes that have no companion objects: ${ex.getMessage}")
        None
    }


  }


  private val LOGGER = LoggerFactory.getLogger(getClass)
}
