package com.stoufexis.subtub.model

import cats.data.*
import org.http4s.*

import com.stoufexis.subtub.typeclass.*

/** String of more than 3 characters. First three characters are the partition key. All of the characters
  * comprise the prefix.
  */
opaque type StreamId = String

extension (sid: StreamId) def string: String = sid

object StreamId:
  inline def apply(str: String): Option[StreamId] =
    Option.when(str.length >= 3)(str)

  given Prefix[StreamId] with
    def prefixHead(a: StreamId): Option[Char] = a.headOption

    def prefixTail(a: StreamId): StreamId = a.drop(1)

    def prefix(a: StreamId): String = a

  given ShardKey[StreamId] with
    def hashKey(a: StreamId): Int = a.take(3).##

  given QueryParamDecoder[StreamId] with
    def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, StreamId] =
      StreamId(value.value) match
        case None =>
          Validated.Invalid(NonEmptyList.of(ParseFailure(value.value, "Could not parse StreamId")))

        case Some(str) => 
          Validated.Valid(str)