package com.stoufexis.subtub.util

import cats.*
import fs2.*

given Traverse[Set] with
  def foldLeft[A, B](fa: Set[A], b: B)(f: (B, A) => B): B =
    fa.foldLeft(b)(f)

  def foldRight[A, B](fa: Set[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
    fa.foldRight(lb)(f)

  def traverse[G[_]: Applicative, A, B](fa: Set[A])(f: A => G[B]): G[Set[B]] =
    fa.foldLeft(Applicative[G].pure(Set.empty[B])): (acc, a) =>
      Applicative[G].map2(f(a), acc)((b, set) => set + b)

extension [F[_], A, B](pipe: Pipe[F, A, B])
  def evalContramapFilter[A0](f: A0 => F[Option[A]]): Pipe[F, A0, B] =
    inp => pipe(inp.evalMapFilter(f))
    