package com.stoufexis.subtub.http

import _root_.io.circe.*
import _root_.io.circe.syntax.*
import cats.*
import cats.data.*
import cats.effect.*
import cats.implicits.given
import fs2.*
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.{EntityDecoder, Response}
import org.typelevel.log4cats.Logger

import com.stoufexis.subtub.broker.Broker
import com.stoufexis.subtub.model.*

import scala.concurrent.duration.*

object Server:
  def routes[F[_]](ws: WebSocketBuilder2[F], broker: Broker[F])(using
    F:   Temporal[F],
    log: Logger[F]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    given EntityDecoder[F, List[Message]] = jsonOf

    object StreamsParam:
      def unapply(params: Map[String, collection.Seq[String]]): Option[Seq[String]] =
        params.get("streams").map(_.toSeq)

    object MaxQueuedParam extends QueryParamDecoderMatcher[MaxQueued]("max_queued")

    def frame(msg: Message): WebSocketFrame =
      WebSocketFrame.Text(msg.asJson.printWith(Printer.noSpaces))

    // Probably should contain a payload which is matched with the Pong responses
    val pingStream: Stream[F, WebSocketFrame] =
      Stream.awakeDelay[F](30.seconds) as WebSocketFrame.Ping()

    def subscribeTo(streams: NonEmptySet[StreamId], maxQueued: MaxQueued): Stream[F, WebSocketFrame] =
      broker.subscribe(streams, maxQueued).map(frame)

    val ignored: Pipe[F, WebSocketFrame, Nothing] =
      _.evalMapFilter(fr => log.warn(s"Ignoring received message: $fr") as None)

    def decoded(streams: Seq[String])(f: NonEmptySet[StreamId] => F[Response[F]]): F[Response[F]] =
      streams.traverse(StreamId(_)) match
        case Some(s) if s.isEmpty => BadRequest("No streams requested")
        case Some(s)              => f(NonEmptySet.of(s.head, s.tail*))
        case None                 => BadRequest("Malformed stream id")

    HttpRoutes.of:
      case GET -> Root / "subscribe" :? StreamsParam(streams) +& MaxQueuedParam(mq) =>
        decoded(streams)(s => ws.build(Stream(subscribeTo(s, mq), pingStream).parJoinUnbounded, ignored))

      case req @ POST -> Root / "publish" =>
        req.as[List[Message]].flatMap(broker.publish(_)) >> Ok()
