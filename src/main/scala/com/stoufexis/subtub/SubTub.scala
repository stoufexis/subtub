package com.stoufexis.subtub

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.kernel.Unique.Token
import cats.effect.std.*
import cats.implicits.given
import fs2.*
import fs2.concurrent.*

import com.stoufexis.subtub.data.*
import com.stoufexis.subtub.model.*

import scala.collection.Set
import scala.collection.concurrent.TrieMap

trait SubTub[F[_]]:
  def publish1(key: StreamId, message: Message): F[Unit]

  def publish(key: StreamId): Pipe[F, Message, Nothing]

  def subscribe(key: StreamId, maxQueued: Int): Stream[F, (StreamId, Message)]

  def subscribe(keys: Set[StreamId], maxQueued: Int): Stream[F, (StreamId, Message)]

object SubTub:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], T: Unique[F]): F[SubTub[F]] =

    type Subscriber = Queue[F, (StreamId, Message)]

    extension (pm: SignallingPrefixMapRef[F, StreamId, Unique.Token, Subscriber])
      def subscribeToAll(keys: Set[StreamId], sub: (Unique.Token, Subscriber)): F[Unit] =
        keys.toList.traverse_(sid => pm(sid).update(_.updateAt(sub._1, sub._2)))

      def unsubscribeFromAll(keys: Set[StreamId], token: Unique.Token): F[Unit] =
        keys.toList.traverse_(sid => pm(sid).update(_.removeAt(token)))

    for
      topics <- SignallingPrefixMapRef[F, StreamId, Unique.Token, Subscriber](shardCount)
    yield new:
      def publish1(key: StreamId, message: Message): F[Unit] =
        topics(key).get.flatMap(_.getMatching.traverse_(_.offer((key, message))))

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
          topics(key).discrete.pull.uncons1.flatMap:
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
        Stream.eval(newSubscriber(maxQueued)).flatMap: sub =>
          Stream
            .eval(topics.subscribeToAll(keys, sub))
            .flatMap(_ => Stream.fromQueueUnterminated(sub._2))
            .onFinalize(topics.unsubscribeFromAll(keys, sub._1))

      def subscribe(key: StreamId, maxQueued: Int): Stream[F, (StreamId, Message)] =
        subscribe(Set(key), maxQueued)
