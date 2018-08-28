
package org.s4s0l.betelgeuse.akkacommons

import org.s4s0l.betelgeuse.akkacommons.BgVersionProvider.BgVersionsInfo
import org.s4s0l.betelgeuse.utils.VersionUtils

/**
  * @author Marcin Wielgus
  */

object BgVersionProvider {

  case class BgVersionsInfo(
                             bgVersions: VersionUtils.Versions,
                             appVersions: VersionUtils.Versions
                           )

}

trait BgVersionProvider {
  self: BgService =>

  lazy val getVersionsInfo: BgVersionsInfo = {
    BgVersionsInfo(
      bgVersions = VersionUtils.getVersions(classOf[BgService]),
      appVersions = VersionUtils.getVersions(getClass))
  }

}
