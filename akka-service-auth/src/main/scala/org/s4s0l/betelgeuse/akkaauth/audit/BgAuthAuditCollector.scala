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

package org.s4s0l.betelgeuse.akkaauth.audit

import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistence
import org.s4s0l.betelgeuse.akkacommons.streaming.BgStreaming

/**
  * @author Marcin Wielgus
  */
class BgAuthAuditCollector
  extends BgStreaming {
  this: BgPersistence =>

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthStreamingAudit[_]])

  override protected def onInitialized(): Unit = {
    super.onInitialized()
    val access = createDefaultKafkaAccess[String, StreamingAuditDto]()
    closeOnShutdown(access)
    val s2d = StreamingAudit2Db(access.consumer)
    s2d.registerOnShutdown(this)
  }

}
