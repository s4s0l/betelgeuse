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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import java.util.{Date, UUID}

import akka.Done
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.TokenManager
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class TokenManagerImplTest extends BgTestRoach with ScalaFutures {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.second, 300.millis)
  private val aService = testWith(new BgPersistenceJournalRoach with BgClusteringSharding {
    lazy val tokenManager: TokenManager = TokenManagerImpl.start

    override protected def initialize(): Unit = {
      super.initialize()
      tokenManager
    }
  })

  def sampleToken(tokenId: TokenId) =
    TokenInfo(
      expiration = new Date(),
      issuedAt = new Date(),
      issuer = Some("issuer"),
      tokenType = AccessToken(tokenId, SerializedToken("1234"))
    )


  feature("Token manager can persist generated token information") {
    scenario("Smoke test") {
      val tokenId = TokenId("tokenId1")
      val userId = UserId("userId")
      new WithService(aService) {
        private val tokenSaved = service.tokenManager.saveToken(sampleToken(tokenId), userId)
        whenReady(tokenSaved) { done =>
          assert(done == Done)
        }
        whenReady(service.tokenManager.isValid(tokenId)) { ok =>
          assert(ok)
        }
        whenReady(service.tokenManager.getSubject(tokenId)) { subject =>
          assert(subject == userId)
        }
        whenReady(service.tokenManager.revokeToken(tokenId)) { ok =>
          assert(ok == Done)
        }
        whenReady(service.tokenManager.isValid(tokenId)) { ok =>
          assert(!ok)
        }
        whenReady(service.tokenManager.getSubject(tokenId)) { subject =>
          assert(subject == userId)
        }
        whenReady(service.tokenManager.revokeToken(tokenId)) { ok =>
          assert(ok == Done)
        }
        private val tokenSaved2 = service.tokenManager.saveToken(sampleToken(tokenId), userId)
        whenReady(tokenSaved2.failed) { ex =>
          assert(ex.getMessage.contains("duplicate token"))
        }
        whenReady(service.tokenManager
          .revokeToken(TokenId(UUID.randomUUID().toString)).failed) { ex =>
          assert(ex.getMessage.contains("not exist"))
        }
      }
      aService.restartService()
      new WithService(aService) {
        whenReady(service.tokenManager.isValid(tokenId)) { ok =>
          assert(!ok)
        }
      }
    }
  }

}
