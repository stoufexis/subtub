package com.stoufexis.subtub.util

import fs2.*

extension [F[_], A, B](pipe: Pipe[F, A, B])
  def evalContramapFilter[A0](f: A0 => F[Option[A]]): Pipe[F, A0, B] =
    inp => pipe(inp.evalMapFilter(f))
    