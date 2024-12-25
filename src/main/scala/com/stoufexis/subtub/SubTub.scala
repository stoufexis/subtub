package com.stoufexis.subtub

import cats.*
import cats.effect.*
import cats.effect.std.*
import cats.implicits.given
import fs2.*

import com.stoufexis.subtub.data.*
import com.stoufexis.subtub.model.*

import scala.collection.Set
import org.typelevel.log4cats.Logger

trait SubTub[F[_]]:
  def publish1(key: StreamId, message: Message): F[Unit]

  def publish(key: StreamId): Pipe[F, Message, Nothing]

  def subscribe(key: StreamId, maxQueued: Int): Stream[F, (StreamId, Message)]

  def subscribe(keys: Set[StreamId], maxQueued: Int): Stream[F, (StreamId, Message)]

object SubTub:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], T: Unique[F], log: Logger[F]): F[SubTub[F]] =

    type Subscriber = Queue[F, (StreamId, Message)]

    extension (pm: SignallingPrefixMapRef[F, StreamId, Unique.Token, Subscriber])
      def subscribeToAll(keys: Set[StreamId], sub: (Unique.Token, Subscriber)): F[Unit] =
        keys.toList.traverse_ : sid => 
          log.info(s"new subscription to ${sid.string}") >>
          pm.get(sid).update(_.updateAt(sub._1, sub._2))

      def unsubscribeFromAll(keys: Set[StreamId], token: Unique.Token): F[Unit] =
        keys.toList.traverse_ : sid =>
          log.info(s"remove subscription from ${sid.string}") >>
          pm.get(sid).update(_.removeAt(token))

    for
      topics <- SignallingPrefixMapRef[F, StreamId, Unique.Token, Subscriber](shardCount)
    yield new:
      def publish1(key: StreamId, message: Message): F[Unit] =
        topics.get(key).get.flatMap(_.getMatching.traverse_(_.offer((key, message))))

      def publish(key: StreamId): Pipe[F, Message, Nothing] =
        def loop(
          msgOrSt: Stream[F, Either[Chunk[Message], PositionedPrefixMap[Unique.Token, Subscriber]]],
          subs:    List[Subscriber]
        ): Pull[F, Nothing, Unit] =
          msgOrSt.pull.uncons1.flatMap:
            case None =>
              Pull.done

            case Some((Left(messages), tail)) =>
              Pull.eval(subs.traverse_(s => messages.traverse_(m => s.offer((key, m))))) >>
                loop(tail, subs)

            case Some((Right(state), tail)) =>
              loop(tail, state.getMatching)

        def publishLoop(messages: Stream[F, Message]): Pull[F, Nothing, Unit] =
          topics.get(key).discrete.pull.uncons1.flatMap:
            case None =>
              Pull.done

            case Some((init, updates)) =>
              loop(
                updates.map(Right(_)).mergeHaltBoth(messages.chunks.map(Left(_))),
                init.getMatching
              )

        publishLoop(_).stream

      end publish

      def newSubscriber(maxQueued: Int): F[(Unique.Token, Subscriber)] =
        for
          t <- T.unique
          q <- Queue.circularBuffer[F, (StreamId, Message)](maxQueued)
        yield (t, q)

      def subscribe(keys: Set[StreamId], maxQueued: Int): Stream[F, (StreamId, Message)] =
        Stream.eval(newSubscriber(maxQueued)).flatMap:
          case sub @ (token, queue) =>
            Stream
              .eval(topics.subscribeToAll(keys, sub))
              .flatMap(_ => Stream.fromQueueUnterminated(queue))
              .onFinalize(topics.unsubscribeFromAll(keys, token))

      def subscribe(key: StreamId, maxQueued: Int): Stream[F, (StreamId, Message)] =
        subscribe(Set(key), maxQueued)
