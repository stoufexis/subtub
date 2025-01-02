package com.stoufexis.subtub.broker

import cats.*
import cats.data.*
import cats.effect.*
import cats.implicits.given
import fs2.*
import org.typelevel.log4cats.Logger

import com.stoufexis.subtub.model.*

trait Broker[F[_]]:
  def publish(keys: NonEmptySet[StreamId], messages: List[Message]): F[Unit]

  def publish(keys: NonEmptySet[StreamId], message: Message): F[Unit] =
    publish(keys, List(message))

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
      override def publish(keys: NonEmptySet[StreamId], messages: List[Message]): F[Unit] =
        keys.traverse_ : key =>
          state.get(key).flatMap: (c: Chain[Subscriber[F]]) =>
            c.traverse_(q => messages.traverse_(msg => q.offer(key -> msg)))

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
