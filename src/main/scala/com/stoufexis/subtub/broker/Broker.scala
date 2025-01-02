package com.stoufexis.subtub.broker

import cats.*
import cats.data.*
import cats.effect.*
import cats.implicits.given
import fs2.*
import org.typelevel.log4cats.Logger

import com.stoufexis.subtub.model.*

trait Broker[F[_]]:
  def publish1(keys: NonEmptySet[StreamId], message: Message): F[Unit]

  def publish(keys: NonEmptySet[StreamId]): Pipe[F, Message, Nothing]

  /** Registers the internal queue but delays pulling until the stream is evaluated
    */
  def subscribeWithoutPulling(
    keys:      NonEmptySet[StreamId],
    maxQueued: MaxQueued
  ): F[Stream[F, (StreamId, Message)]]

  def subscribe(keys: NonEmptySet[StreamId], maxQueued: MaxQueued): Stream[F, (StreamId, Message)]

object Broker:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], T: Unique[F], log: Logger[F]): F[Broker[F]] =
    for state <- BrokerState[F](shardCount) yield new:
      override def publish1(keys: NonEmptySet[StreamId], message: Message): F[Unit] =
        keys.traverse_ : key =>
          state.get(key).flatMap(_.traverse_(_.offer(key -> message)))

      override def publish(keys: NonEmptySet[StreamId]): Pipe[F, Message, Nothing] =
        if keys.size == 1
        then publishStream(keys.head)
        else _.broadcastThrough(keys.toList.map(publishStream)*).drain

      override def subscribeWithoutPulling(
        keys:      NonEmptySet[StreamId],
        maxQueued: MaxQueued
      ): F[Stream[F, (StreamId, Message)]] =
        val newSubscriber: F[(Unique.Token, Subscriber[F])] =
          for
            q <- Subscriber[F](maxQueued)
            _ <- log.info(s"new subscriptions to $keys")
            t <- state.subscribeToAll(keys, q)
          yield (t, q)

        newSubscriber.map: (token, queue) =>
          val finalizeLog: F[Unit] =
            log.info(s"removing subscriptions from $keys")

          Stream
            .fromQueueUnterminated(queue)
            .onFinalize(finalizeLog >> state.unsubscribeFromAll(keys, token))

      override def subscribe(
        keys:      NonEmptySet[StreamId],
        maxQueued: MaxQueued
      ): Stream[F, (StreamId, Message)] =
        Stream.eval(subscribeWithoutPulling(keys, maxQueued)).flatten

      def publishStream(key: StreamId): Pipe[F, Message, Nothing] =
        def loop(
          msgOrSt: Stream[F, Either[Chunk[Message], Chain[Subscriber[F]]]],
          subs:    Chain[Subscriber[F]]
        ): Pull[F, Nothing, Unit] =
          msgOrSt.pull.uncons1.flatMap:
            case None                      => Pull.done
            case Some((Right(subs), tail)) => loop(tail, subs)
            case Some((Left(messages), tail)) =>
              Pull.eval(subs.traverse_(s => messages.traverse_(m => s.offer((key, m))))) >>
                loop(tail, subs)

        def publishLoop(messages: Stream[F, Message]): Pull[F, Nothing, Unit] =
          state.getUpdates(key).pull.uncons1.flatMap:
            case None => Pull.done
            case Some((initSubs, updates)) =>
              loop(updates.map(Right(_)) mergeHaltBoth messages.chunks.map(Left(_)), initSubs)

        publishLoop(_).stream
