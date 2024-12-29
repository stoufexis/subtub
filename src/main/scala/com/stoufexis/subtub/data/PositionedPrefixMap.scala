package com.stoufexis.subtub.data

trait PositionedPrefixMap[K, V]:
  def upper: List[V]

  def node: Map[K, V]

  def isEmpty: Boolean

  def removeAt(key: K): PositionedPrefixMap[K, V]

  def updateAt(key: K, value: V): PositionedPrefixMap[K, V]

  def getMatching: List[V]

object PositionedPrefixMap:
  def apply[P, K, V](pm: PrefixMap[P, K, V], posAt: P): PositionedPrefixMap[K, V] =
    apply(pm.positionedAt(posAt))

  def apply[P, K, V](pm: (PrefixMap[P, K, V], List[V])): PositionedPrefixMap[K, V] = new:
    def upper: List[V] = pm._2

    def node: Map[K, V] = pm._1.node

    def isEmpty: Boolean = pm._1.isEmpty

    def removeAt(key: K): PositionedPrefixMap[K, V] = apply((pm._1.removeAt(key), upper))

    def updateAt(key: K, value: V): PositionedPrefixMap[K, V] = apply((pm._1.updateAt(key, value), upper))

    def getMatching: List[V] = upper ++ pm._1.getMatching
