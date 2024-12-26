package com.stoufexis.subtub.model

import com.stoufexis.subtub.typeclass.*

/** String of more than 3 characters. First three characters are the partition key. All of the characters
  * comprise the prefix.
  */
case class StreamId(shardKey: String, parts: IArray[String])

extension (sid: StreamId)
  def string: String =
    sid.parts.mkString(":")

object StreamId:
  inline def apply(str: String): Option[StreamId] =
    str.split(":") match
      case arr if arr.length == 1 && str.endsWith(":") =>
        Some(StreamId(str, IArray(str)))

      case arr if arr.length > 1 =>
        Some(StreamId(arr(0), IArray.unsafeFromArray(arr)))

      case _ => None

  given Prefix[StreamId] with
    def prefixHead(s: StreamId): Option[String] = s.parts.headOption

    def prefixTail(s: StreamId): StreamId = s.copy(parts = s.parts.drop(1))

  given ShardKey[StreamId] with
    def hashKey(a: StreamId): Int = a.shardKey.##
