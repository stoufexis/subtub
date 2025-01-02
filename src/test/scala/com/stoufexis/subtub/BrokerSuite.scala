package com.stoufexis.subtub

import cats.data.*
import cats.effect.IO
import cats.implicits.given
import fs2.*
import org.typelevel.log4cats.Logger
import weaver.*

import com.stoufexis.subtub.broker.Broker
import com.stoufexis.subtub.model.*

import scala.concurrent.duration.*

import java.util.concurrent.TimeoutException

object BrokerSuite extends SimpleIOSuite:
  val q100: MaxQueued = MaxQueued(100)

  def streamId(str: String): StreamId =
    StreamId(str).getOrElse(sys.error("Bug in the test"))

  given Logger[IO] with
    def error(t: Throwable)(message: => String): IO[Unit] = IO.unit
    def warn(t:  Throwable)(message: => String): IO[Unit] = IO.unit
    def info(t:  Throwable)(message: => String): IO[Unit] = IO.unit
    def debug(t: Throwable)(message: => String): IO[Unit] = IO.unit
    def trace(t: Throwable)(message: => String): IO[Unit] = IO.unit

    def error(message: => String): IO[Unit] = IO.unit
    def warn(message:  => String): IO[Unit] = IO.unit
    def info(message:  => String): IO[Unit] = IO.unit
    def debug(message: => String): IO[Unit] = IO.unit
    def trace(message: => String): IO[Unit] = IO.unit

  test("late subscribers receive no messages"):
    val stream: StreamId = streamId("a:b")

    for
      b <- Broker[IO](100)
      _ <- b.publish(Message(stream, "Hello"))
      o <- b.subscribe(NonEmptySet.of(stream), q100).timeoutOnPullTo(1.second, Stream.empty).compile.toList
    yield expect(o.isEmpty)

  def routingTest(shardCount: Int): IO[Expectations] =
    def msg(s: StreamId): Message = Message(s, "hello")

    val stream0: StreamId = streamId("a:")
    val stream1: StreamId = streamId("a:b")
    val stream2: StreamId = streamId("a:b:c")
    val stream3: StreamId = streamId("a:b:c:d")
    val stream4: StreamId = streamId("a:b:d")
    val stream5: StreamId = streamId("f:g:h")
    val stream6: StreamId = streamId("b:c")

    extension (b: Broker[IO])
      def publishAll: IO[Unit] =
        b.publish(msg(stream0)) >>
          b.publish(msg(stream1)) >>
          b.publish(msg(stream2)) >>
          b.publish(msg(stream3)) >>
          b.publish(msg(stream4)) >>
          b.publish(msg(stream5)) >>
          b.publish(msg(stream6))

      def subToAll: IO[List[Stream[IO, (StreamId, Message)]]] =
        List(
          b.subscribeDeferred(NonEmptySet.of(stream0), q100).map(_.map((stream0, _))),
          b.subscribeDeferred(NonEmptySet.of(stream1), q100).map(_.map((stream1, _))),
          b.subscribeDeferred(NonEmptySet.of(stream2), q100).map(_.map((stream2, _))),
          b.subscribeDeferred(NonEmptySet.of(stream3), q100).map(_.map((stream3, _))),
          b.subscribeDeferred(NonEmptySet.of(stream4), q100).map(_.map((stream4, _))),
          b.subscribeDeferred(NonEmptySet.of(stream5), q100).map(_.map((stream5, _))),
          b.subscribeDeferred(NonEmptySet.of(stream6), q100).map(_.map((stream6, _)))
        ).sequence

    extension (l: List[Stream[IO, (StreamId, Message)]])
      /** Outputs a map with structure Map[subscribed stream id, Map[published stream id, message]]
        */
      def collectAll(take: Int, timeout: FiniteDuration): IO[Map[StreamId, List[Message]]] =
        Stream.iterable(l)
          .parJoinUnbounded
          .take(take)
          .timeout(timeout)
          .compile
          .toList
          .map(_.groupBy(_._1).fmap(_.map(_._2)))

    val expectation: Map[StreamId, List[Message]] = Map(
      stream0 -> List(msg(stream0), msg(stream1), msg(stream2), msg(stream3), msg(stream4)),
      stream1 -> List(msg(stream0), msg(stream1), msg(stream2), msg(stream3), msg(stream4)),
      stream2 -> List(msg(stream0), msg(stream1), msg(stream2), msg(stream3)),
      stream3 -> List(msg(stream0), msg(stream1), msg(stream2), msg(stream3)),
      stream4 -> List(msg(stream0), msg(stream1), msg(stream4)),
      stream5 -> List(msg(stream5)),
      stream6 -> List(msg(stream6))
    )

    val messageCount: Int =
      expectation.toList.flatMap(_._2.toList).length

    for
      b <- Broker[IO](shardCount)
      s <- b.subToAll
      _ <- b.publishAll
      // In case we receive less than the expected amount the subscribers will hang forever
      // To force the test to terminate in those cases, we apply a timeout
      o <- s.collectAll(messageCount, 1.second)
    yield expect.all(
      o(stream0) == expectation(stream0),
      o(stream1) == expectation(stream1),
      o(stream2) == expectation(stream2),
      o(stream3) == expectation(stream3),
      o(stream4) == expectation(stream4),
      o(stream5) == expectation(stream5),
      o(stream6) == expectation(stream6)
    )
  end routingTest

  test(
    "shard count of 100: a message gets routed from a publisher to a subscriber when there is a common prefix between the published and subscribed stream ids"
  )(routingTest(100))

  test(
    "shard count of 1: a message gets routed from a publisher to a subscriber when there is a common prefix between the published and subscribed stream ids"
  )(routingTest(1))

  test("publish and subscribe to multiple stream ids works like single ones"):
    def msg(s: StreamId): Message = Message(s, s"Hello")

    val stream0: StreamId = streamId("a:")
    val stream1: StreamId = streamId("a:b")
    val stream2: StreamId = streamId("a:c")
    val stream3: StreamId = streamId("a:d")

    for
      b  <- Broker[IO](100)
      s  <- b.subscribeDeferred(NonEmptySet.of(stream1, stream2, stream3), q100)
      _  <- b.publish(msg(stream0))
      o1 <- s.take(3).map(_._1).compile.toList

      s1 <- b.subscribeDeferred(NonEmptySet.of(stream0), q100)
      _  <- b.publish(List(stream1, stream2, stream3).map(msg))
      o2 <- s1.take(3).map(_._1).compile.toList
    yield expect.all(
      o1.sortBy(_.string) == List.fill(3)(stream0),
      o2.sortBy(_.string) == List(stream1, stream2, stream3)
    )

  test("overflown subscriber queues drop old messages"):
    def msg(s: StreamId, i: Int): Message =
      Message(s, s"Hello: $i")

    val stream: StreamId = streamId("a:")

    extension (b: Broker[IO])
      def publish10: IO[Unit] =
        List.range(0, 10).map(i => b.publish(msg(stream, i))).sequence_

      def suscribeNoPull(maxQueued: MaxQueued): IO[Stream[IO, Message]] =
        b.subscribeDeferred(NonEmptySet.of(stream), maxQueued)

    extension (st: Stream[IO, Message])
      def collectAll(take: Int, timeout: FiniteDuration): IO[List[Message]] =
        st.take(take)
          .timeout(timeout)
          .compile
          .toList

    for
      b <- Broker[IO](100)
      s <- b.suscribeNoPull(MaxQueued(1))
      _ <- b.publish10
      o <- s.collectAll(1, 1.second)
    yield expect(o == List(msg(stream, 9)))

  test("publishStream routes to timely subscribers"):
    val stream0: StreamId = streamId("a:")
    val stream1: StreamId = streamId("a:b")
    val stream2: StreamId = streamId("a:c")
    val stream3: StreamId = streamId("a:d")

    def msg(s: StreamId, i: Int): Message =
      Message(s, s"Hello: $i")

    def messages(s: StreamId): Stream[IO, Message] =
      Stream.eval(IO.sleep(100.millis) as msg(s, 1)) ++
        Stream.eval(IO.sleep(100.millis) as msg(s, 2)) ++
        Stream.eval(IO.sleep(100.millis) as msg(s, 3))

    extension (b: Broker[IO])
      def publishAll: IO[Unit] =
        messages(stream0).evalTap(b.publish).compile.drain

      def suscribeNoPull(stream: StreamId): IO[Stream[IO, Message]] =
        b.subscribeDeferred(NonEmptySet.of(stream), q100)

    extension (st: Stream[IO, Message])
      def toList(take: Int, timeout: FiniteDuration): IO[List[Message]] =
        st.take(take).timeout(timeout).compile.toList

    for
      b <- Broker[IO](100)
      _ <- b.publishAll.start

      _  <- IO.sleep(50.millis)
      s1 <- b.suscribeNoPull(stream1)
      _  <- IO.sleep(100.millis)
      s2 <- b.suscribeNoPull(stream2)
      _  <- IO.sleep(100.millis)
      s3 <- b.suscribeNoPull(stream3)
      _  <- IO.sleep(100.millis)
      s4 <- b.suscribeNoPull(stream3)

      o1 <- s1.toList(3, 100.millis)
      o2 <- s2.toList(2, 100.millis)
      o3 <- s3.toList(1, 100.millis)
      o4 <- s4.toList(1, 100.millis).recover { case _: TimeoutException => Nil }
    yield expect.all(
      o1 == List(msg(stream0, 1), msg(stream0, 2), msg(stream0, 3)),
      o2 == List(msg(stream0, 2), msg(stream0, 3)),
      o3 == List(msg(stream0, 3)),
      o4 == Nil
    )
