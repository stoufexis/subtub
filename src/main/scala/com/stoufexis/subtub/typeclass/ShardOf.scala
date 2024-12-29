package com.stoufexis.subtub.typeclass

/** Type class for retrieving the shard of a type.
  */
trait ShardOf[A]:
  def shard(a: A, count: Int): Int

extension [A: ShardOf](a: A)
  def shard(count: Int): Int = summon[ShardOf[A]].shard(a, count)
