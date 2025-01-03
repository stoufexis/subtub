package com.stoufexis.subtub.util

import cats.*
import fs2.*

extension [F[_], A, B](pipe: Pipe[F, A, B])
  def evalContramapFilter[A0](f: A0 => F[Option[A]]): Pipe[F, A0, B] =
    inp => pipe(inp.evalMapFilter(f))

extension [F[_], A](fa: F[A])(using F: Foldable[F])
  def foldLeftM_[G[_], B](z: B)(f: (B, A) => G[B])(implicit G: Monad[G]): G[Unit] =
    G.void(F.foldLeftM(fa, z)(f))
