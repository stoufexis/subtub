package com.stoufexis.subtub.data

import com.stoufexis.subtub.data.PrefixMap.empty
import com.stoufexis.subtub.typeclass.*

// TODO: test, optimize
case class PrefixMap[P: Prefix, K, V](
  val node:    Map[K, V],
  val subtree: Map[Char, PrefixMap[P, K, V]]
):
  private def updatedSubtree(c: Char)(f: Option[PrefixMap[P, K, V]] => Option[PrefixMap[P, K, V]])
    : PrefixMap[P, K, V] =
    PrefixMap(node, subtree.updatedWith(c)(f))

  def positionedAt(p: P): PrefixMap[P, K, V] =
    p.prefixHead match
      case None    => this
      case Some(c) => subtree.get(c).fold(empty)(_.positionedAt(p.prefixTail))

  def isEmpty(p: P): Boolean =
    p.prefixHead match
      case None    => isEmpty
      case Some(c) => subtree.get(c).fold(true)(_.isEmpty(p.prefixTail))

  def isEmpty: Boolean =
    node.isEmpty && subtree.isEmpty

  def nodeAt(p: P): Map[K, V] =
    p.prefixHead match
      case None    => node
      case Some(c) => subtree.get(c).fold(Map.empty)(_.nodeAt(p.prefixTail))

  def replaceNodeAt(p: P, replacement: Map[K, V]): PrefixMap[P, K, V] =
    p.prefixHead match
      case None => PrefixMap(replacement, subtree)
      case Some(c) =>
        updatedSubtree(c):
          case Some(pm) =>
            Some(pm.replaceNodeAt(p.prefixTail, replacement)).filterNot(_.isEmpty)
          case None =>
            Option.when(replacement.nonEmpty)(PrefixMap.empty.replaceNodeAt(p.prefixTail, replacement))

  def removeAt(p: P, key: K): PrefixMap[P, K, V] =
    p.prefixHead match
      case None => removeAt(key)
      case Some(c) =>
        updatedSubtree(c):
          case Some(pm) => Some(pm.removeAt(p.prefixTail, key)).filterNot(_.isEmpty)
          case None     => None

  def removeAt(key: K): PrefixMap[P, K, V] =
    PrefixMap(node.removed(key), subtree)

  def updateAt(p: P, key: K, value: V): PrefixMap[P, K, V] =
    p.prefixHead match
      case None => updateAt(p, key, value)
      case Some(c) =>
        updatedSubtree(c):
          case Some(pm) => Some(pm.updateAt(p.prefixTail, key, value))
          case None     => Some(PrefixMap.empty.updateAt(p.prefixTail, key, value))

  def updateAt(key: K, value: V): PrefixMap[P, K, V] =
    PrefixMap(node.updated(key, value), subtree)

  def getMatching(p: P): List[V] =
    p.prefixHead match
      case None    => getMatching
      case Some(c) => subtree.get(c).toList.flatMap(_.getMatching(p.prefixTail))

  def getMatching: List[V] =
    node.values.toList ++ subtree.values.flatMap(_.getMatching).toList

object PrefixMap:
  def empty[P: Prefix, K, V]: PrefixMap[P, K, V] =
    PrefixMap(Map.empty, Map.empty)
