/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.sql.{Connection, Types}

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect}
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.internal.dbsupport.JdbcTemplate

/**
  * @author Marcin Wielgus
  */
@Aspect
class FlywayAspects {
  @Around("execution(* org.flywaydb.core.internal.dbsupport.DbSupportFactory.createDbSupport(..)) && args(connection, printInfo)")
  def onSingleRequest(pjp: ProceedingJoinPoint, connection: Connection, printInfo: Boolean): Any = {
    try {
      pjp.proceed()
    } catch {
      case a: FlywayException if a.getMessage.startsWith("Unsupported Database: Crate") =>
        new CrateDbSupport(new JdbcTemplate(connection, Types.NULL))
    }
  }

  @Around("execution(* org.flywaydb.core.internal.util.scanner.classpath.FileSystemClassPathLocationScanner.findResourceNames(..)) && args(*, *)")
  def disableFileSystemClasspath(pjp: ProceedingJoinPoint): Any = {
      pjp.proceed()

  }
}
