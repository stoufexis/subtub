package com.stoufexis.subtub.model

import cats.data.*
import cats.kernel.CommutativeGroup
import org.http4s.*

opaque type MaxQueued = Int

object MaxQueued:
  val default: MaxQueued = 10

  inline def apply(int: Int): MaxQueued =
    inline if withinRange(int) then
      int
    else
      scala.compiletime.error("Size outside of range")

  extension (mq: MaxQueued) def get: Int = mq

  private inline def withinRange(i: Int): Boolean =
    0 < i && i <= 250

  given CommutativeGroup[MaxQueued] =
    cats.instances.int.catsKernelStdGroupForInt

  given QueryParamDecoder[MaxQueued] with
    def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, MaxQueued] =
      value.value.toIntOption match
        case None =>
          Validated.Invalid(NonEmptyList.of(ParseFailure(value.value, "Not an int")))

        case Some(i) if withinRange(i) =>
          Validated.Valid(i)

        case Some(i) =>
          Validated.Invalid(NonEmptyList.of(ParseFailure(value.value, "Value is larger than 250")))
