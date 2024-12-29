package com.stoufexis.subtub.broker

import cats.data.*
import cats.effect.*
import cats.effect.kernel.Unique.Token
import cats.implicits.given
import fs2.*
import fs2.concurrent.SignallingRef

import com.stoufexis.subtub.data.*
import com.stoufexis.subtub.model.*
import com.stoufexis.subtub.typeclass.*

trait BrokerState[F[_]]:
  def get(id: StreamId): F[List[Subscriber[F]]]

  def getUpdates(id: StreamId): Stream[F, List[Subscriber[F]]]

  def subscribeToAll(keys: NonEmptySet[StreamId], sub: Subscriber[F]): F[Token]

  def unsubscribeFromAll(keys: NonEmptySet[StreamId], token: Token): F[Unit]

object BrokerState:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], U: Unique[F]): F[BrokerState[F]] =
    type PMap  = PrefixMap[StreamId, Token, Subscriber[F]]
    type PPMap = PositionedPrefixMap[Token, Subscriber[F]]

    List
      .fill(shardCount)(SignallingRef[F].of(PrefixMap.empty: PMap))
      .sequence
      .map: (list: List[SignallingRef[F, PMap]]) =>
        new:
          val shardCount: Int =
            list.length

          val arr: IArray[SignallingRef[F, PMap]] =
            IArray.from(list)

          val refFunction: StreamId => SignallingRef[F, PMap] =
            p => arr(p.shard(shardCount))

          val positioned: StreamId => PMap => PPMap =
            p => pm => PositionedPrefixMap(pm, p)

          val recombine: StreamId => PMap => PPMap => PMap =
            p => pm => ppm => pm.replaceNodeAt(p, ppm.node)

          val lensedRef: StreamId => SignallingRef[F, PPMap] =
            p => SignallingRef.lens(refFunction(p))(positioned(p), recombine(p))

          def get(id: StreamId): F[List[Subscriber[F]]] =
            lensedRef(id).get.map(_.getMatching)

          def getUpdates(id: StreamId): Stream[F, List[Subscriber[F]]] =
            lensedRef(id).discrete.map(_.getMatching)

          def subscribeToAll(keys: NonEmptySet[StreamId], sub: Subscriber[F]): F[Token] =
            for
              token <- U.unique
              _     <- keys.traverse_(lensedRef(_).update(_.updateAt(token, sub)))
            yield token

          def unsubscribeFromAll(keys: NonEmptySet[StreamId], token: Token): F[Unit] =
            keys.traverse_(lensedRef(_).update(_.removeAt(token)))
