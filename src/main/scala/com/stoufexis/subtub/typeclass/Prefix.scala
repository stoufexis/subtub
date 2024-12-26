package com.stoufexis.subtub.typeclass

trait Prefix[A]:
  def prefixHead(a: A): Option[String]

  def prefixTail(a: A): A

extension [A: Prefix](a: A)
  def prefixHead: Option[String] = summon[Prefix[A]].prefixHead(a)

  def prefixTail: A = summon[Prefix[A]].prefixTail(a)