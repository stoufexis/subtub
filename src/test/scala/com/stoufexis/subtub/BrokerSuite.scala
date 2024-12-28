package com.stoufexis.subtub

import cats.effect.IO
import cats.implicits.given
import fs2.*
import org.typelevel.log4cats.Logger
import weaver.*

import com.stoufexis.subtub.model.*

import scala.concurrent.duration.*

object BrokerSuite extends SimpleIOSuite:
  // TODO: Test behavior of broker with 1 shard vs many

  def streamId(str: String): StreamId =
    StreamId(str).getOrElse(sys.error("Bug in the test"))

  given Logger[IO] with
    def error(t:       Throwable)(message: => String): IO[Unit] = IO.unit
    def warn(t:        Throwable)(message: => String): IO[Unit] = IO.unit
    def info(t:        Throwable)(message: => String): IO[Unit] = IO.unit
    def debug(t:       Throwable)(message: => String): IO[Unit] = IO.unit
    def trace(t:       Throwable)(message: => String): IO[Unit] = IO.unit
    def error(message: => String): IO[Unit] = IO.unit
    def warn(message:  => String): IO[Unit] = IO.unit
    def info(message:  => String): IO[Unit] = IO.unit
    def debug(message: => String): IO[Unit] = IO.unit
    def trace(message: => String): IO[Unit] = IO.unit

  test("late subscribers receive no messages"):
    val stream: StreamId = streamId("a:b")

    for
      b <- Broker[IO](100)
      _ <- b.publish1(Set(stream), Message("Hello"))
      o <- b.subscribe(Set(stream), 100).timeoutOnPullTo(1.second, Stream.empty).compile.toList
    yield expect(o.isEmpty)

  def routingTest(shardCount: Int): IO[Expectations] =
    val msg: Message = Message("hello")

    val stream0: StreamId = streamId("a:")
    val stream1: StreamId = streamId("a:b")
    val stream2: StreamId = streamId("a:b:c")
    val stream3: StreamId = streamId("a:b:c:d")
    val stream4: StreamId = streamId("a:b:d")
    val stream5: StreamId = streamId("f:g:h")
    val stream6: StreamId = streamId("b:c")

    extension (b: Broker[IO])
      def publishAll: IO[Unit] =
        b.publish1(Set(stream0), msg) >>
          b.publish1(Set(stream1), msg) >>
          b.publish1(Set(stream2), msg) >>
          b.publish1(Set(stream3), msg) >>
          b.publish1(Set(stream4), msg) >>
          b.publish1(Set(stream5), msg) >>
          b.publish1(Set(stream6), msg)

    extension (b: Broker[IO])
      /** Outputs a map with structure Map[subscribed stream id, Map[published stream id, message]]
        */
      def collectAll(take: Int, timeout: FiniteDuration): IO[Map[StreamId, Map[StreamId, Message]]] =
        Stream(
          b.subscribe(Set(stream0), 100).map((stream0, _)),
          b.subscribe(Set(stream1), 100).map((stream1, _)),
          b.subscribe(Set(stream2), 100).map((stream2, _)),
          b.subscribe(Set(stream3), 100).map((stream3, _)),
          b.subscribe(Set(stream4), 100).map((stream4, _)),
          b.subscribe(Set(stream5), 100).map((stream5, _)),
          b.subscribe(Set(stream6), 100).map((stream6, _))
        )
          .parJoinUnbounded
          .take(take)
          .timeout(timeout)
          .compile
          .toList
          .map(_.groupBy(_._1).fmap(_.map(_._2).toMap))

    val expectation: Map[StreamId, Map[StreamId, Message]] = Map(
      stream0 -> Map(stream0 -> msg, stream1 -> msg, stream2 -> msg, stream3 -> msg, stream4 -> msg),
      stream1 -> Map(stream0 -> msg, stream1 -> msg, stream2 -> msg, stream3 -> msg, stream4 -> msg),
      stream2 -> Map(stream0 -> msg, stream1 -> msg, stream2 -> msg, stream3 -> msg),
      stream3 -> Map(stream0 -> msg, stream1 -> msg, stream2 -> msg, stream3 -> msg),
      stream4 -> Map(stream0 -> msg, stream1 -> msg, stream4 -> msg),
      stream5 -> Map(stream5 -> msg),
      stream6 -> Map(stream6 -> msg)
    )

    val messageCount: Int =
      expectation.toList.flatMap(_._2.toList).length

    for
      b <- Broker[IO](shardCount)
      // waits half a second to make sure all subscribers have registered
      // this is much more time than it realistically takes, but we are keeping it simple for testing
      _ <- (IO.sleep(500.millis) >> b.publishAll).start
      // In case we receive less than the expected amount the subscribers will hang forever
      // To force the test to terminate in those cases, we apply a timeout
      o <- b.collectAll(messageCount, 1.second)
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
    val msg: Message = Message(s"Hello")

    val stream0: StreamId = streamId("a:")
    val stream1: StreamId = streamId("a:b")
    val stream2: StreamId = streamId("a:c")
    val stream3: StreamId = streamId("a:d")

    for
      b  <- Broker[IO](100)
      s  <- b.subscribeWithoutPulling(Set(stream1, stream2, stream3), 100)
      _  <- b.publish1(Set(stream0), msg)
      o1 <- s.take(3).map(_._1).compile.toList

      s1 <- b.subscribeWithoutPulling(Set(stream0), 100)
      _  <- b.publish1(Set(stream1, stream2, stream3), msg)
      o2 <- s1.take(3).map(_._1).compile.toList
    yield expect.all(
      o1.sortBy(_.string) == List.fill(3)(stream0),
      o2.sortBy(_.string) == List(stream1, stream2, stream3)
    )

  test("overflown subscriber queues drop old messages"):
    def msg(i: Int): Message =
      Message(s"Hello: $i")

    val stream: StreamId = streamId("a:")

    extension (b: Broker[IO])
      def publish10: IO[Unit] =
        List.range(0, 10).map(i => b.publish1(Set(stream), msg(i))).sequence_

      def suscribeNoPull(maxQueued: Int): IO[Stream[IO, (StreamId, Message)]] =
        b.subscribeWithoutPulling(Set(stream), maxQueued)

    extension (st: Stream[IO, (StreamId, Message)])
      def collectAll(take: Int, timeout: FiniteDuration): IO[List[(StreamId, Message)]] =
        st.take(take)
          .timeout(timeout)
          .compile
          .toList

    for
      b <- Broker[IO](100)
      s <- b.suscribeNoPull(1)
      _ <- b.publish10
      o <- s.collectAll(1, 1.second)
    yield expect(o == List(stream -> msg(9)))
