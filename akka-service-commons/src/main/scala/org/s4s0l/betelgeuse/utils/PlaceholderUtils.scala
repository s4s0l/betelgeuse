package org.s4s0l.betelgeuse.utils

import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.internal.util.PlaceholderReplacer
import org.flywaydb.core.internal.util.scanner.classpath.ClassPathResource

/**
  * @author Marcin Wielgus
  */
trait PlaceholderUtils {

  def placeholderResourceConfig(resourceName: String, params: Map[String, String]): Config = {
    val source: String = new ClassPathResource(resourceName, getClass.getClassLoader).loadAsString("UTF-8")
    import scala.collection.JavaConverters._
    val sourceNoPlaceholders: String = new PlaceholderReplacer(params.asJava, "${", "}").replacePlaceholders(source)
    ConfigFactory.parseString(sourceNoPlaceholders)
  }

}
