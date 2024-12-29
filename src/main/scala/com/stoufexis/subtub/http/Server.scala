package com.stoufexis.subtub.http

import _root_.io.circe.*
import _root_.io.circe.parser.*
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

import com.stoufexis.subtub.Broker
import com.stoufexis.subtub.model.*
import com.stoufexis.subtub.util.*

import scala.concurrent.duration.*

object Server:
  def routes[F[_]](ws: WebSocketBuilder2[F], broker: Broker[F])(using
    F:   Temporal[F],
    log: Logger[F]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    given EntityDecoder[F, Message] = jsonOf

    object StreamsParam:
      def unapply(params: Map[String, collection.Seq[String]]): Option[Seq[String]] =
        params.get("streams").map(_.toSeq)

    def frame(sid: StreamId, msg: Message): WebSocketFrame =
      val json: Json =
        Json.obj("published_to" -> sid.string.asJson).deepMerge(msg.asJson)

      WebSocketFrame.Text(json.printWith(Printer.noSpaces))

    // Probably should contain a payload, and then the Pong responses verified against it
    val pingStream: Stream[F, WebSocketFrame] =
      Stream.awakeDelay[F](30.seconds) as WebSocketFrame.Ping()

    def subscribeTo(streams: NonEmptySet[StreamId]): Stream[F, WebSocketFrame] =
      broker.subscribe(streams, 10).map(frame)

    def publishTo(streams: NonEmptySet[StreamId]): Pipe[F, WebSocketFrame, Nothing] =
      broker.publish(streams).evalContramapFilter:
        case WebSocketFrame.Text((str, true)) =>
          decode[Message](str) match
            case Left(err)  => log.warn(err)(s"Failed to decode frame") as None
            case Right(msg) => F.pure(Some(msg))

        case fr =>
          log.warn(s"Ignoring received message: $fr") as None

    val ignored: Pipe[F, WebSocketFrame, Nothing] =
      _.evalMapFilter(fr => log.warn(s"Ignoring received message: $fr") as None)

    def decoded(streams: Seq[String])(f: NonEmptySet[StreamId] => F[Response[F]]): F[Response[F]] =
      streams.traverse(StreamId(_)) match
        case Some(s) if s.isEmpty => BadRequest("No streams requested")
        case Some(s)              => f(NonEmptySet.of(s.head, s.tail*))
        case None                 => BadRequest("Malformed stream id")

    HttpRoutes.of:
      case GET -> Root / "subscribe" :? StreamsParam(streams) =>
        decoded(streams)(s => ws.build(Stream(subscribeTo(s), pingStream).parJoinUnbounded, ignored))

      case GET -> Root / "publish_stream" :? StreamsParam(streams) =>
        decoded(streams)(s => ws.build(pingStream, publishTo(s)))

      case req @ POST -> Root / "publish_one" :? StreamsParam(streams) =>
        decoded(streams)(s => req.as[Message].flatMap(broker.publish1(s, _)) >> Ok())
