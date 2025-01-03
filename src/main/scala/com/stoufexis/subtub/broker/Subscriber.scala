package com.stoufexis.subtub.broker

import cats.effect.*
import cats.effect.std.Queue

import com.stoufexis.subtub.model.*

type Subscriber[F[_]] = Queue[F, Message]

object Subscriber:
  def apply[F[_]: Concurrent](maxQueued: MaxQueued): F[Subscriber[F]] =
    Queue.circularBuffer(maxQueued.get)
