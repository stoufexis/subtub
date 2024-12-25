package com.stoufexis.subtub

import cats.effect.*
import cats.implicits.given
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.stoufexis.subtub.http.*

object SubTub extends IOApp.Simple:
  def server(broker: Broker[IO])(using Logger[IO]): Resource[IO, Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpWebSocketApp(ws => Server.routes(ws, broker).orNotFound)
      .build
      .void

  def resource: Resource[IO, Unit] =
    for
      given Logger[IO] <- Resource.eval(Slf4jLogger.create[IO])
      broker           <- Resource.eval(Broker[IO](100))
      _                <- Resource.eval(Logger[IO].info("Starting up!"))
      _                <- server(broker)
    yield ()

  def run: IO[Unit] =
    resource.useForever
