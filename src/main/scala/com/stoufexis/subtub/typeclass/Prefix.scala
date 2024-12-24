package com.stoufexis.subtub.typeclass

trait Prefix[A]:
  def prefix(a: A): String

  def prefixHead(a: A): Option[Char]

  def prefixTail(a: A): A

extension [A: Prefix](a: A)
  def prefixHead: Option[Char] = summon[Prefix[A]].prefixHead(a)

  def prefixTail: A = summon[Prefix[A]].prefixTail(a)

  def prefix: String = summon[Prefix[A]].prefix(a)