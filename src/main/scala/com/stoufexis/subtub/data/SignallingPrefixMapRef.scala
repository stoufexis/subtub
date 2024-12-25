package com.stoufexis.subtub.data

import cats.effect.*
import cats.implicits.given
import fs2.concurrent.SignallingRef

import com.stoufexis.subtub.typeclass.*
import cats.kernel.*
import cats.kernel.Monoid

trait SignallingPrefixMapRef[F[_], P, K, V]:
  def get(p: P): SignallingRef[F, PositionedPrefixMap[K, V]]

  def collectFromAll[A: Monoid](f: PrefixMap[P, K, V] => A): F[A]

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

      val positioned: P => PrefixMap[P, K, V] => PositionedPrefixMap[K, V] =
        p => pm => PositionedPrefixMap(pm.positionedAt(p))

      val recombine: P => PrefixMap[P, K, V] => PositionedPrefixMap[K, V] => PrefixMap[P, K, V] =
        p => pm => ppm => pm.replaceNodeAt(p, ppm.node)

      new:
        def get(p: P): SignallingRef[F, PositionedPrefixMap[K, V]] =
          SignallingRef.lens(refFunction(p))(positioned(p), recombine(p))

        def collectFromAll[A: Monoid](f: PrefixMap[P, K, V] => A): F[A] =
          list.traverse(_.get.map(f)).map(_.combineAll)
