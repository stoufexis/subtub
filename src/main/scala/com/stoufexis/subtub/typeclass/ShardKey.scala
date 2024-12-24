package com.stoufexis.subtub.typeclass

trait ShardKey[A]:
  def hashKey(a: A): Int

extension [A: ShardKey](a: A)
  def hashKey: Int = summon[ShardKey[A]].hashKey(a)
