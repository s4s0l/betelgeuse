/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-04 08:37
 *
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