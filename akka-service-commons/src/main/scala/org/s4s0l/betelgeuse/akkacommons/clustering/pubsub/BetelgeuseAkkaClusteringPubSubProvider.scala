/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-04 08:56
 *
 */

package org.s4s0l.betelgeuse.akkacommons.clustering.pubsub

import akka.actor.ActorRef

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseAkkaClusteringPubSubProvider {
  def getDefaultPubSubMediator: ActorRef
}
