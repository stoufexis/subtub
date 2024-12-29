package com.stoufexis.subtub

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.*
import cats.implicits.given
import fs2.*
import org.typelevel.log4cats.Logger

import com.stoufexis.subtub.data.*
import com.stoufexis.subtub.model.*

trait Broker[F[_]]:
  def publish1(keys: NonEmptySet[StreamId], message: Message): F[Unit]

  def publish(keys: NonEmptySet[StreamId]): Pipe[F, Message, Nothing]

  /** Registers the internal queue but delays pulling until the stream is evaluated
    */
  def subscribeWithoutPulling(keys: NonEmptySet[StreamId], maxQueued: Int): F[Stream[F, (StreamId, Message)]]

  def subscribe(keys: NonEmptySet[StreamId], maxQueued: Int): Stream[F, (StreamId, Message)]

object Broker:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], T: Unique[F], log: Logger[F]): F[Broker[F]] =

    type Subscriber = Queue[F, (StreamId, Message)]

    extension (pm: SignallingPrefixMapRef[F, StreamId, Unique.Token, Subscriber])
      def subscribeToAll(keys: NonEmptySet[StreamId], sub: (Unique.Token, Subscriber)): F[Unit] =
        keys.toList.traverse_ : sid =>
          log.info(s"new subscription to ${sid.string}") >>
            pm.get(sid).update(_.updateAt(sub._1, sub._2))

      def unsubscribeFromAll(keys: NonEmptySet[StreamId], token: Unique.Token): F[Unit] =
        keys.toList.traverse_ : sid =>
          log.info(s"remove subscription from ${sid.string}") >>
            pm.get(sid).update(_.removeAt(token))

    for
      topics <- SignallingPrefixMapRef[F, StreamId, Unique.Token, Subscriber](shardCount)
    yield new:
      override def publish1(keys: NonEmptySet[StreamId], message: Message): F[Unit] =
        keys.traverse_ : key =>
          topics
            .get(key)
            .get
            .flatMap(_.getMatching.traverse_(_.offer((key, message))))

      override def publish(keys: NonEmptySet[StreamId]): Pipe[F, Message, Nothing] =
        if keys.size == 1
        then publishStream(keys.head)
        else _.broadcastThrough(keys.toList.map(publishStream)*).drain

      override def subscribeWithoutPulling(
        keys:      NonEmptySet[StreamId],
        maxQueued: Int
      ): F[Stream[F, (StreamId, Message)]] =
        val newSubscriber: F[(Unique.Token, Subscriber)] =
          for
            t <- T.unique
            q <- Queue.circularBuffer[F, (StreamId, Message)](maxQueued)
            _ <- topics.subscribeToAll(keys, (t, q))
          yield (t, q)

        newSubscriber.map: (token, queue) =>
          Stream
            .fromQueueUnterminated(queue)
            .onFinalize(topics.unsubscribeFromAll(keys, token))

      override def subscribe(keys: NonEmptySet[StreamId], maxQueued: Int): Stream[F, (StreamId, Message)] =
        Stream.eval(subscribeWithoutPulling(keys, maxQueued)).flatten

      def publishStream(key: StreamId): Pipe[F, Message, Nothing] =
        def loop(
          msgOrSt: Stream[F, Either[Chunk[Message], PositionedPrefixMap[Unique.Token, Subscriber]]],
          subs:    List[Subscriber]
        ): Pull[F, Nothing, Unit] =
          msgOrSt.pull.uncons1.flatMap:
            case None                       => Pull.done
            case Some((Right(state), tail)) => loop(tail, state.getMatching)
            case Some((Left(messages), tail)) =>
              Pull.eval(subs.traverse_(s => messages.traverse_(m => s.offer((key, m))))) >>
                loop(tail, subs)

        def publishLoop(messages: Stream[F, Message]): Pull[F, Nothing, Unit] =
          topics.get(key).discrete.pull.uncons1.flatMap:
            case None => Pull.done
            case Some((init, updates)) =>
              loop(
                updates.map(Right(_)).mergeHaltBoth(messages.chunks.map(Left(_))),
                init.getMatching
              )

        publishLoop(_).stream
