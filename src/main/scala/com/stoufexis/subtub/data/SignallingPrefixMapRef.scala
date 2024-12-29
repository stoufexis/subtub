package com.stoufexis.subtub.data

import cats.*
import cats.effect.*
import cats.implicits.given
import fs2.concurrent.SignallingRef

import com.stoufexis.subtub.typeclass.*

trait SignallingPrefixMapRef[F[_], P, K, V]:
  def get(p: P): SignallingRef[F, PositionedPrefixMap[K, V]]

object SignallingPrefixMapRef:
  def apply[F[_]: Concurrent, P: Prefix: ShardOf, K, V](shardCount: Int)
    : F[SignallingPrefixMapRef[F, P, K, V]] =
    List
      .fill(shardCount)(SignallingRef[F].of(PrefixMap.empty))
      .sequence
      .map(fromRefs[F, P, K, V])

  def fromRefs[F[_]: Applicative, P: Prefix: ShardOf, K, V](refs: List[SignallingRef[F, PrefixMap[P, K, V]]])
    : SignallingPrefixMapRef[F, P, K, V] =
    val shardCount: Int =
      refs.length

    val arr: IArray[SignallingRef[F, PrefixMap[P, K, V]]] =
      IArray.from(refs)

    val refFunction: P => SignallingRef[F, PrefixMap[P, K, V]] =
      p => arr(p.shard(shardCount))

    val positioned: P => PrefixMap[P, K, V] => PositionedPrefixMap[K, V] =
      p => pm => PositionedPrefixMap(pm, p)

    val recombine: P => PrefixMap[P, K, V] => PositionedPrefixMap[K, V] => PrefixMap[P, K, V] =
      p => pm => ppm => pm.replaceNodeAt(p, ppm.node)

    p => SignallingRef.lens(refFunction(p))(positioned(p), recombine(p))
