package org.s4s0l.betelgeuse.akkacommons.persistence.journal

/**
  * @author Marcin Wielgus
  */
trait JournalCallback {
  /**
    * Called after this event is restored from journal
    *
    * @param e Jurnalntity as looked when was in database
    * @return has to return new instance with updated values, or self if nothing modified
    */
  def restored(e: ScalikeAsyncWriteJournalEntity): Any
}
