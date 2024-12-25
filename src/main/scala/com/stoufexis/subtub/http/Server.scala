package com.stoufexis.subtub.http

import _root_.io.circe.*
import _root_.io.circe.parser.*
import _root_.io.circe.syntax.*
import cats.*
import cats.effect.*
import cats.implicits.given
import fs2.*
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger

import com.stoufexis.subtub.SubTub
import com.stoufexis.subtub.model.*
import com.stoufexis.subtub.util.{*, given}

import scala.concurrent.duration.*

object Server:
  def routes[F[_]](ws: WebSocketBuilder2[F], subtub: SubTub[F])(using
    F:   Temporal[F],
    log: Logger[F]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    object StreamsParam:
      def unapply(params: Map[String, collection.Seq[String]]): Option[Set[String]] =
        params.get("streams").map(_.toSet)

    object StreamParam extends QueryParamDecoderMatcher[String]("stream")

    def frame(sid: StreamId, msg: Message): WebSocketFrame =
      val json: Json =
        Json.obj("published_to" -> sid.string.asJson).deepMerge(msg.asJson)

      WebSocketFrame.Text(json.printWith(Printer.noSpaces))

    def fromFrame(frame: WebSocketFrame.Text): F[Option[Message]] =
      decode[Message](frame.str) match
        case Left(err)  => log.warn(err)(s"Failed to decode frame") as None
        case Right(msg) => F.pure(Some(msg))

    // Probably should contain a payload, and then the Pong responses verified against it
    val pingStream: Stream[F, WebSocketFrame] =
      Stream.awakeDelay[F](30.seconds) as WebSocketFrame.Ping()

    def subscribeTo(streams: Set[StreamId]): Stream[F, WebSocketFrame] =
      subtub.subscribe(streams, 10).map(frame)

    def publishTo(stream: StreamId): Pipe[F, WebSocketFrame, Nothing] =
      subtub.publish(stream).evalContramapFilter:
        case fr @ WebSocketFrame.Text((_, true)) => fromFrame(fr)
        case fr                                  => log.warn(s"Ignoring received message: $fr") as None

    val ignored: Pipe[F, WebSocketFrame, Nothing] =
      _.evalMapFilter(fr => log.warn(s"Ignoring received message: $fr") as None)

    HttpRoutes.of:
      case GET -> Root / "subscribe" :? StreamsParam(streams) =>
        streams.traverse(StreamId(_)) match
          case Some(streams) => ws.build(Stream(subscribeTo(streams), pingStream).parJoinUnbounded, ignored)
          case None          => BadRequest("Bad stream id")

      case GET -> Root / "publish" :? StreamParam(stream) =>
        StreamId(stream) match
          case Some(stream) => ws.build(pingStream, publishTo(stream))
          case None         => BadRequest("Bad stream id")
