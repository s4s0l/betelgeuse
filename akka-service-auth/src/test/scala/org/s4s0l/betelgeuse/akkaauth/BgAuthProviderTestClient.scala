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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkaauth

import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.manager.AdditionalUserAttrsManager
import org.s4s0l.betelgeuse.akkacommons.BgServiceId

/**
  * @author Marcin Wielgus
  */
class BgAuthProviderTestClient extends BgAuthClient[String] {

  override protected def systemName: String = "BgAuthProviderTestClient"

  override protected def portBase: Int = 2

  override def customizeConfiguration
  : Config = super.customizeConfiguration
    .withFallback(clusteringClientCreateConfig(bgAuthProviderServiceId))

  override protected def bgAuthProviderServiceId
  : BgServiceId = BgServiceId("BgAuthProviderTestProvider", 1)

  override protected def jwtAttributeMapper: AdditionalUserAttrsManager[String] =
    SampleJwtAttributes
}