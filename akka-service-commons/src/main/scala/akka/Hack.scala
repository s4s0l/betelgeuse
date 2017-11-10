/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package akka

import akka.cluster.client.ClusterReceptionist.Internal.Contacts

import scala.collection.immutable

/**
  * @author Marcin Wielgus
  */
object Hack {
  def contacts(contactPoints: immutable.IndexedSeq[String]): Any = {
    Contacts(contactPoints)
  }



}
