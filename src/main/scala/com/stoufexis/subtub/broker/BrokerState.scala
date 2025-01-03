package com.stoufexis.subtub.broker

import cats.data.*
import cats.effect.*
import cats.effect.kernel.Unique.Token
import cats.implicits.given

import com.stoufexis.subtub.data.*
import com.stoufexis.subtub.model.*
import com.stoufexis.subtub.typeclass.*

trait BrokerState[F[_]]:
  def get(id: StreamId): F[Chain[Subscriber[F]]]

  def subscribeToAll(keys: NonEmptySet[StreamId], sub: Subscriber[F]): F[Token]

  def unsubscribeFromAll(keys: NonEmptySet[StreamId], token: Token): F[Unit]

object BrokerState:
  def apply[F[_]](shardCount: Int)(using F: Concurrent[F], U: Unique[F]): F[BrokerState[F]] =
    type PMap = PrefixMap[StreamId, Token, Subscriber[F]]

    List
      .fill(shardCount)(Ref[F].of(PrefixMap.empty: PMap))
      .sequence
      .map: (list: List[Ref[F, PMap]]) =>
        new:
          val arr: IArray[Ref[F, PMap]] =
            IArray.from(list)

          def shard(id: StreamId): Ref[F, PMap] =
            arr(id.shard(arr.length))

          def get(id: StreamId): F[Chain[Subscriber[F]]] =
            shard(id).get.map(_.getMatching(id))

          def subscribeToAll(keys: NonEmptySet[StreamId], sub: Subscriber[F]): F[Token] =
            for
              token <- U.unique
              _     <- keys.traverse_(k => shard(k).update(_.updateAt(k, token, sub)))
            yield token

          def unsubscribeFromAll(keys: NonEmptySet[StreamId], token: Token): F[Unit] =
            keys.traverse_(k => shard(k).update(_.removeAt(k, token)))
