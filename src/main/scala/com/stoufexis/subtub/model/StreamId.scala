package com.stoufexis.subtub.model

import com.stoufexis.subtub.typeclass.*

case class StreamId(partitionKey: String, name: String)

object StreamId:
  given Prefix[StreamId] with
    def prefixHead(a: StreamId): Option[Char] = a.name.headOption

    def prefixTail(a: StreamId): StreamId = a.copy(name = a.name.drop(1))

    def prefix(a: StreamId): String = a.name

  given ShardKey[StreamId] with
    def hashKey(a: StreamId): Int = a.partitionKey.##