package org.s4s0l.betelgeuse.akkacommons.persistence.journal

/**
  * @author Marcin Wielgus
  */

class JournalFailureException(message: String, cause: Throwable) extends Exception(message, cause)

class JurnalDuplicateKeyException(message: String, cause: Throwable) extends JournalFailureException(message, cause)
