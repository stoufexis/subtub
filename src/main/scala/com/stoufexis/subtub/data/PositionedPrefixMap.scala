package com.stoufexis.subtub.data

trait PositionedPrefixMap[K, V]:
  def node: Map[K, V]

  def isEmpty: Boolean

  def removeAt(key: K): PositionedPrefixMap[K, V]

  def updateAt(key: K, value: V): PositionedPrefixMap[K, V]

  def getMatching: List[V]

  def allNodes: List[Map[K, V]]

object PositionedPrefixMap:
  def apply[P, K, V](pm: PrefixMap[P, K, V]): PositionedPrefixMap[K, V] = new:
    def node: Map[K, V] = pm.node

    def isEmpty: Boolean = pm.isEmpty

    def removeAt(key: K): PositionedPrefixMap[K, V] = apply(pm.removeAt(key))

    def updateAt(key: K, value: V): PositionedPrefixMap[K, V] = apply(pm.updateAt(key, value))

    def getMatching: List[V] = pm.getMatching

    def allNodes: List[Map[K, V]] = pm.allNodes

  def apply[P, K, V](pm: PrefixMap[P, K, V], posAt: P): PositionedPrefixMap[K, V] =
    apply(pm.positionedAt(posAt))