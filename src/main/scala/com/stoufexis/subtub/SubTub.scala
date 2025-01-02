package com.stoufexis.subtub

import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.*
import cats.implicits.given
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.stoufexis.subtub.broker.Broker
import com.stoufexis.subtub.config.Config
import com.stoufexis.subtub.http.*

object SubTub extends IOApp.Simple:
  def errorHandler(t: Throwable, msg: => String)(using log: Logger[IO]): IO[Unit] =
    log.error(t)(msg)

  def withErrorLogging(http: HttpApp[IO])(using Logger[IO]) = ErrorHandling.Recover.total(
    ErrorAction.log(
      http,
      messageFailureLogAction = errorHandler,
      serviceErrorLogAction   = errorHandler
    )
  )

  def server(broker: Broker[IO], cfg: Config)(using log: Logger[IO]): Resource[IO, Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(cfg.bindHost)
      .withPort(cfg.bindPort)
      .withHttpWebSocketApp(ws => withErrorLogging(Server.routes(ws, broker).orNotFound))
      .build
      .void

  def readConfig: IO[Config] =
    Config.load(sys.env) match
      case Valid(cfg) =>
        IO.pure(cfg)

      case Invalid(errs) =>
        IO.raiseError(RuntimeException(("Errors when loading config:" :: errs).mkString_("\n")))

  def resource: Resource[IO, Unit] =
    for
      given Logger[IO] <- Resource.eval(Slf4jLogger.create[IO])
      cfg              <- Resource.eval(readConfig)
      broker           <- Resource.eval(Broker[IO](cfg.shardCount))
      _                <- Resource.eval(Logger[IO].info("Starting up!"))
      _                <- server(broker, cfg)
    yield ()

  def run: IO[Unit] =
    resource.useForever
