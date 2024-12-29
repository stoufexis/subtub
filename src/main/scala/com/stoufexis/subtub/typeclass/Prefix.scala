package com.stoufexis.subtub.typeclass

trait Prefix[A]:
  def prefixHead(a: A): Option[String]

  def prefixTail(a: A): A

extension [A: Prefix](a: A)
  def prefixHead: Option[String] = summon[Prefix[A]].prefixHead(a)

  def prefixTail: A = summon[Prefix[A]].prefixTail(a)

object Prefix:
  given Prefix[String] with
    def prefixHead(a: String): Option[String] =
      if a.isEmpty then None else Some(a.take(1))

    def prefixTail(a: String): String =
      a.drop(1)
