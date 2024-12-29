package com.stoufexis.subtub.config

import cats.data.*
import cats.implicits.given
import com.comcast.ip4s.*

case class Config(
  bindHost:   Hostname,
  bindPort:   Port,
  shardCount: Int
)

object Config:
  def load(env: Map[String, String]): ValidatedNel[String, Config] =
    val hostVar  = "SUBTUB_BIND_HOST"
    val portVar  = "SUBTUB_BIND_PORT"
    val shardVar = "SUBTUB_SHARD_COUNT"

    val host: Option[Hostname] =
      env.get(hostVar)
        .flatMap(Hostname.fromString)

    val port: Option[Port] =
      env.get(portVar)
        .flatMap(Port.fromString)

    val shard: Option[Int] =
      env.get(shardVar)
        .flatMap(_.toIntOption)

    def failNote(varName: String): NonEmptyList[String] =
      NonEmptyList.of(s"Did not find or could not parse $varName")

    (
      Validated.fromOption(host, failNote(hostVar)),
      Validated.fromOption(port, failNote(portVar)),
      Validated.fromOption(shard, failNote(shardVar))
    ).mapN(Config(_, _, _))
