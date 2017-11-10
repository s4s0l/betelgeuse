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



package org.s4s0l.betelgeuse.akkacommons.http.stomp

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaHttpStompExtension(private val system: ExtendedActorSystem) extends Extension {

}


object BetelgeuseAkkaHttpStompExtension extends ExtensionId[BetelgeuseAkkaHttpStompExtension] with ExtensionIdProvider {


  override def get(system: ActorSystem): BetelgeuseAkkaHttpStompExtension = system.extension(this)

  override def apply(system: ActorSystem): BetelgeuseAkkaHttpStompExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaHttpStompExtension.type = BetelgeuseAkkaHttpStompExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaHttpStompExtension =
    new BetelgeuseAkkaHttpStompExtension(system)

}