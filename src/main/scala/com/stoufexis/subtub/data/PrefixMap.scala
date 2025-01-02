package com.stoufexis.subtub.data

import cats.data.*

import com.stoufexis.subtub.data.PrefixMap.empty
import com.stoufexis.subtub.typeclass.*

// TODO: test, optimize
case class PrefixMap[P: Prefix, K, V](
  val node:    Map[K, V],
  val subtree: Map[String, PrefixMap[P, K, V]]
):
  private def updatedSubtree(c: String)(f: Option[PrefixMap[P, K, V]] => Option[PrefixMap[P, K, V]])
    : PrefixMap[P, K, V] =
    PrefixMap(node, subtree.updatedWith(c)(f))

  def isEmpty: Boolean =
    node.isEmpty && subtree.isEmpty

  def nodeAt(p: P): Map[K, V] =
    p.prefixHead match
      case None    => node
      case Some(s) => subtree.get(s).fold(Map.empty)(_.nodeAt(p.prefixTail))

  def replaceNodeAt(p: P, replacement: Map[K, V]): PrefixMap[P, K, V] =
    p.prefixHead match
      case None => PrefixMap(replacement, subtree)
      case Some(s) =>
        updatedSubtree(s):
          case Some(pm) =>
            Some(pm.replaceNodeAt(p.prefixTail, replacement)).filterNot(_.isEmpty)
          case None =>
            Option.when(replacement.nonEmpty)(empty.replaceNodeAt(p.prefixTail, replacement))

  def removeAt(p: P, key: K): PrefixMap[P, K, V] =
    p.prefixHead match
      case None => PrefixMap(node.removed(key), subtree)
      case Some(s) =>
        updatedSubtree(s):
          case Some(pm) => Some(pm.removeAt(p.prefixTail, key)).filterNot(_.isEmpty)
          case None     => None

  def updateAt(p: P, key: K, value: V): PrefixMap[P, K, V] =
    p.prefixHead match
      case None => PrefixMap(node.updated(key, value), subtree)
      case Some(s) =>
        updatedSubtree(s):
          case Some(pm) => Some(pm.updateAt(p.prefixTail, key, value))
          case None     => Some(empty.updateAt(p.prefixTail, key, value))

  def getMatching(p: P): Chain[V] =
    p.prefixHead match
      case None => getMatching
      case Some(s) =>
        val sub = Chain.fromOption(subtree.get(s)).flatMap(_.getMatching(p.prefixTail))
        Chain.fromIterableOnce(node.valuesIterator) ++ sub

  def getMatching: Chain[V] =
    val sub = Chain.fromIterableOnce(subtree.valuesIterator).flatMap(_.getMatching)
    Chain.fromIterableOnce(node.valuesIterator) ++ sub

  def print(root: String, printNode: Map[K, V] => String): List[String] =
    val lines: List[String] =
      for
        (str, prefixMap) <- subtree.toList
        subtreeLine      <- prefixMap.print(str, printNode)
      yield subtreeLine

    (if lines.isEmpty then List("") else lines.map("--" + _)).map: l =>
      s"$root(${printNode(node)})" + l

object PrefixMap:
  def empty[P: Prefix, K, V]: PrefixMap[P, K, V] =
    PrefixMap(Map.empty, Map.empty)
