package com.stoufexis.subtub.data

import cats.effect.*
import cats.implicits.given
import fs2.concurrent.SignallingRef

import com.stoufexis.subtub.typeclass.*

trait SignallingPrefixMapRef[F[_], P, K, V]:
  def apply(p: P): SignallingRef[F, PositionedPrefixMap[K, V]]

object SignallingPrefixMapRef:
  def apply[F[_]: Concurrent, P: Prefix: ShardKey, K, V](
    shardCount: Int
  ): F[SignallingPrefixMapRef[F, P, K, V]] =
    val prefixMaps: F[List[SignallingRef[F, PrefixMap[P, K, V]]]] =
      List
        .fill(shardCount)(SignallingRef[F].of(PrefixMap.empty))
        .sequence

    prefixMaps.map: list =>
      val arr: IArray[SignallingRef[F, PrefixMap[P, K, V]]] =
        IArray.from(list)

      val refFunction: P => SignallingRef[F, PrefixMap[P, K, V]] =
        p => arr(Math.abs(p.hashKey % shardCount))

      val get: P => PrefixMap[P, K, V] => PositionedPrefixMap[K, V] =
        p => pm => PositionedPrefixMap(pm.positionedAt(p))

      val set: P => PrefixMap[P, K, V] => PositionedPrefixMap[K, V] => PrefixMap[P, K, V] =
        p => pm => apm => pm.replaceNodeAt(p, apm.node)

      p => SignallingRef.lens(refFunction(p))(get(p), set(p))
