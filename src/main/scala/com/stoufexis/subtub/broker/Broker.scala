package com.stoufexis.subtub.broker

import cats.*
import cats.data.*
import cats.effect.*
import cats.implicits.given
import fs2.*
import org.typelevel.log4cats.Logger

import com.stoufexis.subtub.model.*
import com.stoufexis.subtub.util.*

trait Broker[F[_]]:
  def publish(messages: List[Message]): F[Unit]

  def publish(message: Message): F[Unit] = publish(List(message))

  /** Registers the internal queue but defers pulling until the stream is evaluated
    */
  def subscribeDeferred(keys: NonEmptySet[StreamId], maxQueued: MaxQueued): F[Stream[F, Message]]

  def subscribe(keys: NonEmptySet[StreamId], maxQueued: MaxQueued): Stream[F, Message]

object Broker:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], T: Unique[F], log: Logger[F]): F[Broker[F]] =
    for state <- BrokerState[F](shardCount) yield new:
      override def publish(messages: List[Message]): F[Unit] =
        messages.foldLeftM_(Map.empty[StreamId, Chain[Subscriber[F]]]): (cache, msg) =>
          cache.get(msg.publish_to) match
            case Some(subs) =>
              subs.traverse(_.offer(msg)) as cache

            case None =>
              state.get(msg.publish_to).flatMap: subs =>
                subs.traverse_(_.offer(msg)) as cache.updated(msg.publish_to, subs)

      override def subscribeDeferred(
        keys:      NonEmptySet[StreamId],
        maxQueued: MaxQueued
      ): F[Stream[F, Message]] =
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
      ): Stream[F, Message] =
        Stream.eval(subscribeDeferred(keys, maxQueued)).flatten
