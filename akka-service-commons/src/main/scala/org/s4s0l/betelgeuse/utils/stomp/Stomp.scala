/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-01 17:48
 *
 */

package org.s4s0l.betelgeuse.utils.stomp

import fastparse.all
import fastparse.all._

/**
  * @author Marcin Wielgus
  */
object Stomp {
  val COMMA = ","
  val V1_0 = "1.0"
  val V1_1 = "1.1"
  val V1_2 = "1.2"
  val DEFAULT_HEART_BEAT = "0,0"
  val DEFAULT_VERSION = "1.0"
  val EMPTY = ""
  val SUPPORTED_PROTOCOL_VERSIONS = Array("1.2", "1.1", "1.0")
  val TRUE = "true"
  val FALSE = "false"
  val END = "end"

  object Commands {
    val STOMP = "STOMP"
    val CONNECT = "CONNECT"
    val SEND = "SEND"
    val DISCONNECT = "DISCONNECT"
    val SUBSCRIBE = "SUBSCRIBE"
    val UNSUBSCRIBE = "UNSUBSCRIBE"

    val BEGIN_TRANSACTION = "BEGIN"
    val COMMIT_TRANSACTION = "COMMIT"
    val ABORT_TRANSACTION = "ABORT"
    val BEGIN = "BEGIN"
    val COMMIT = "COMMIT"
    val ABORT = "ABORT"
    val ACK = "ACK"
    val NACK = "NACK"
    val KEEPALIVE = "KEEPALIVE"

    val parser: all.Parser[Unit] = P(STOMP | CONNECT | SEND | DISCONNECT |
      SUBSCRIBE | UNSUBSCRIBE |
      BEGIN_TRANSACTION | COMMIT_TRANSACTION | ABORT_TRANSACTION |
      BEGIN | COMMIT | ABORT | ACK | NACK | KEEPALIVE)
  }

  object Responses {
    val CONNECTED = "CONNECTED"
    val ERROR = "ERROR"
    val MESSAGE = "MESSAGE"
    val RECEIPT = "RECEIPT"
  }

  object Headers {
    val SEPERATOR = ":"
    val RECEIPT_REQUESTED = "receipt"
    val TRANSACTION = "transaction"
    val CONTENT_LENGTH = "content-length"
    val CONTENT_TYPE = "content-type"
    val TRANSFORMATION = "transformation"
    val TRANSFORMATION_ERROR = "transformation-error"
    val parser: all.Parser[Unit] = P(RECEIPT_REQUESTED | TRANSACTION | CONTENT_LENGTH | CONTENT_TYPE | TRANSFORMATION | TRANSFORMATION_ERROR)

    object Response {
      val RECEIPT_ID = "receipt-id"
    }

    object Send {
      val DESTINATION = "destination"
      val EXPIRATION_TIME = "expires"
      val ID = "receipt"
      val PRIORITY = "priority"
      val TYPE = "type"
      val PERSISTENT = "persistent"
    }

    object Message {
      val MESSAGE_ID = "message-id"
      val ACK_ID = "ack"
      val DESTINATION = "destination"
      val CORRELATION_ID = "correlation-id"
      val EXPIRATION_TIME = "expires"
      val REPLY_TO = "reply-to"
      val PRORITY = "priority"
      val REDELIVERED = "redelivered"
      val TIMESTAMP = "timestamp"
      val TYPE = "type"
      val SUBSCRIPTION = "subscription"
      val BROWSER = "browser"
      val USERID = "JMSXUserID"
      val ORIGINAL_DESTINATION = "original-destination"
      val PERSISTENT = "persistent"
    }

    object Subscribe {
      val DESTINATION = "destination"
      val ACK_MODE = "ack"
      val ID = "id"
      val SELECTOR = "selector"
      val BROWSER = "browser"

      object AckModeValues {
        val AUTO = "auto"
        val CLIENT = "client"
        val INDIVIDUAL = "client-individual"
      }

    }

    object Unsubscribe {
      val DESTINATION = "destination"
      val ID = "id"
    }

    object Connect {
      val LOGIN = "login"
      val PASSCODE = "passcode"
      val CLIENT_ID = "client-id"
      val REQUEST_ID = "request-id"
      val ACCEPT_VERSION = "accept-version"
      val HOST = "host"
      val HEART_BEAT = "heart-beat"
    }

    object Error {
      val MESSAGE = "message"
    }

    object Connected {
      val SESSION = "session"
      val RESPONSE_ID = "response-id"
      val SERVER = "server"
      val VERSION = "version"
      val HEART_BEAT = "heart-beat"
    }

    object Ack {
      val MESSAGE_ID = "message-id"
      val SUBSCRIPTION = "subscription"
      val ID = "id"
    }

  }

}
