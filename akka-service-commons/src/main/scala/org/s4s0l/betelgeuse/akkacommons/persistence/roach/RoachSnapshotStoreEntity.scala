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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseEntityObject
import scalikejdbc.WrappedResultSet

/**
  * @author Marcin Wielgus
  */
class RoachSnapshotStoreEntity(
                                val tag: String,
                                val id: String,
                                val seq: Long,
                                val snapshotTimestamp: Long,
                                val snapshot: String,
                                val snapshotClass: String)


object RoachSnapshotStoreEntity
  extends BetelgeuseEntityObject[RoachSnapshotStoreEntity] {

  override def tableName: String = "state_snapshots"

  override def apply(m: scalikejdbc.ResultName[RoachSnapshotStoreEntity])
                    (rs: WrappedResultSet): RoachSnapshotStoreEntity = {
    new RoachSnapshotStoreEntity(
      rs.string(m.tag),
      rs.string(m.id),
      rs.long(m.seq),
      rs.long(m.snapshotTimestamp),
      rs.string(m.snapshot),
      rs.string(m.snapshotClass)
    )
  }
}

