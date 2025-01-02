package com.stoufexis.subtub.model

import cats.kernel.Order
import io.circe.*

import com.stoufexis.subtub.typeclass.*

import scala.util.hashing.MurmurHash3

/** String of more than 3 characters. First three characters are the partition key. All of the characters
  * comprise the prefix.
  */
case class StreamId(shardKey: String, parts: IArray[String]):
  override def toString(): String = this.string

extension (sid: StreamId)
  def string: String =
    sid.parts.mkString(":")

object StreamId:
  inline def apply(str: String): Option[StreamId] =
    str.split(":") match
      case arr if (arr.length == 1 && str.endsWith(":")) || arr.length > 1 =>
        Some(StreamId(arr(0), IArray.unsafeFromArray(arr)))

      case _ => None

  given Prefix[StreamId] with
    def prefixHead(s: StreamId): Option[String] = s.parts.headOption

    def prefixTail(s: StreamId): StreamId = s.copy(parts = s.parts.drop(1))

  given ShardOf[StreamId] with
    def shard(a: StreamId, count: Int): Int =
      MurmurHash3.stringHash(a.shardKey).abs % count

  given Order[StreamId] = Order.by(_.string)

  given Codec[StreamId] =
    Codec.from(
      Decoder[String].emap(apply(_).toRight("Invalid stream id format")),
      Encoder[String].contramap(_.string)
    )
