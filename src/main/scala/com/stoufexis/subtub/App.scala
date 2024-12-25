package com.stoufexis.subtub

import cats.effect.*
import cats.implicits.given
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.stoufexis.subtub.http.*

object App extends IOApp.Simple:
  def server(subtub: SubTub[IO])(using Logger[IO]): Resource[IO, Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpWebSocketApp(ws => Server.routes(ws, subtub).orNotFound)
      .build
      .void

  def resource: Resource[IO, Unit] =
    for
      given Logger[IO] <- Resource.eval(Slf4jLogger.fromName[IO]("SubTub"))
      subtub           <- Resource.eval(SubTub[IO](100))
      _                <- Resource.eval(Logger[IO].info("Starting up!"))
      _                <- server(subtub)
    yield ()

  def run: IO[Unit] =
    resource.useForever
