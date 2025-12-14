package util


import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ ExecutionContext, Future }

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class FutureUtilsSpec
    extends AnyWordSpec, Matchers, ScalaFutures:

  import FutureUtils.*
  given ExecutionContext = ExecutionContext.global

  "runInBatchesAndDiscard" should {

    "execute all side-effecting operations" in {
      val counter   = new AtomicInteger(0)
      val inputs    = (1 to 10).iterator
      val batchSize = 3

      val f: Int => Future[Unit] = i =>
        Future {
          counter.incrementAndGet()
          ()
        }

      runInBatchesAndDiscard(inputs, batchSize)(f).futureValue

      counter.get() shouldBe 10
    }

    "run batches sequentially and items within a batch concurrently" in {
      val timestamps = new ConcurrentLinkedQueue[(String, Long)]()
      val inputs     = Seq("a", "b", "c", "d").iterator
      val batchSize  = 2

      def f(item: String): Future[Unit] = Future {
        timestamps.add((item, System.nanoTime()))
        ()
      }

      runInBatchesAndDiscard(inputs, batchSize)(f).futureValue

      val recorded    = timestamps.toArray(Array.empty[(String, Long)])
      val batch1Times = recorded.take(2).map(_._2)
      val batch2Times = recorded.drop(2).take(2).map(_._2)

      batch1Times.max should be < batch2Times.min
    }

    "handle an empty input iterator gracefully" in {
      val inputs    = Iterator.empty[Int]
      val batchSize = 3

      val f: Int => Future[Unit] = _ => Future.failed(new RuntimeException("Should not be called"))

      noException should be thrownBy
        runInBatchesAndDiscard(inputs, batchSize)(f).futureValue
    }

    "should process the iterator lazily, not consuming it all at once" in {
      val totalElements    = 20
      val batchSize        = 10
      val elementsConsumed = new AtomicInteger(0)

      val inputs = (1 to totalElements).iterator.map { i =>
        elementsConsumed.incrementAndGet()
        i
      }

      // Dummy async function: returns an immediately successful Future,
      // does NOT pull from the iterator itself.
      val f: Int => Future[Unit] = _ => Future.unit

      runInBatchesAndDiscard(inputs, batchSize)(f)

      // Assertion:
      // - In a lazy implementation (recursive), only the first batch of elements
      //   is pulled from the iterator synchronously (10 elements).
      // - In an eager implementation (foldLeft), all elements would be pulled upfront (20).
      elementsConsumed.get() shouldBe batchSize
    }

  }

  "runInBatchesAndCollect" should {

    "collect all results correctly" in {
      val inputs    = (1 to 6).iterator
      val batchSize = 2

      val f: Int => Future[Int] = i => Future(i * 2)

      val result = runInBatchesAndCollect(inputs, batchSize)(f).futureValue

      result shouldBe Seq(2, 4, 6, 8, 10, 12)
    }

    "preserve input order even when individual futures complete out of order" in {
      val inputs    = (1 to 4).iterator
      val batchSize = 2

      def f(i: Int): Future[Int] = Future {
        Thread.sleep((100 - i * 20).toLong) // 1 = 80ms, 4 = 20ms
        i
      }

      val result = runInBatchesAndCollect(inputs, batchSize)(f).futureValue

      result shouldBe Seq(1, 2, 3, 4)
    }

    "return an empty sequence for an empty input iterator" in {
      val inputs    = Iterator.empty[Int]
      val batchSize = 2

      val f: Int => Future[Int] = i => Future(i * 2)

      val result = runInBatchesAndCollect(inputs, batchSize)(f).futureValue

      result shouldBe empty
    }

    "should process the iterator lazily, not consuming it all at once" in {
      val totalElements    = 20
      val batchSize        = 10
      val elementsConsumed = new AtomicInteger(0)

      val inputs = (1 to totalElements).iterator.map { i =>
        elementsConsumed.incrementAndGet()
        i
      }

      // Dummy async function: returns an immediately successful Future,
      // does NOT pull from the iterator itself.
      val f: Int => Future[String] = i => Future.successful(s"Item $i")

      val future = runInBatchesAndCollect(inputs, batchSize)(f)

      // Assertion:
      // - In a lazy implementation (recursive), only the first batch of elements
      //   is pulled from the iterator synchronously (10 elements).
      // - In an eager implementation (foldLeft), all elements would be pulled upfront (20).
      elementsConsumed.get() shouldBe batchSize

    }

  }

  "runSequentiallyAndDiscard" should {

    "run sequentially: each item starts after the previous one completes" in {
      import scala.jdk.CollectionConverters.*
      val timestamps = new ConcurrentLinkedQueue[(Int, Long)]()
      val inputs     = Seq(1, 2, 3, 4).iterator

      def f(i: Int): Future[Unit] = Future {
        // record the input along with the time we processed it
        timestamps.add((i, System.nanoTime()))
        ()
      }

      runSequentiallyAndDiscard(inputs)(f).futureValue

      val recorded = timestamps.toArray(Array.empty[(Int, Long)])
      val times    = recorded.map(_._2)

      // check that each timestamp is strictly greater than the one before
      times.sliding(2).foreach {
        case Array(prev, next) =>
          next should be > prev
      }
    }

    "handle an empty input iterator gracefully" in {
      val inputs: Iterator[Int]  = Iterator.empty
      val f: Int => Future[Unit] = _ => Future.failed(new RuntimeException("should not be called"))

      noException should be thrownBy
        runSequentiallyAndDiscard(inputs)(f).futureValue
    }

    "should process the iterator lazily, one element at a time" in {
      val totalElements    = 5
      val elementsConsumed = new AtomicInteger(0)

      val inputs = (1 to totalElements).iterator.map { i =>
        elementsConsumed.incrementAndGet()
        i
      }

      // f returns a Future that completes immediately,
      // but the next element should not be pulled until this one runs.
      val f: Int => Future[Unit] = _ => Future.unit

      // kick off but do not await, the first element is pulled synchronously
      val future = runSequentiallyAndDiscard(inputs)(f)

      elementsConsumed.get() shouldBe 1

      // now await completion so that the rest get pulled
      future.futureValue
      elementsConsumed.get() shouldBe totalElements
    }
  }

  "runSequentiallyAndCollect" should {

    "collect all results correctly in order" in {
      val inputs = Seq("a", "b", "c", "d")

      def f(s: String): Future[String] = Future(s.toUpperCase)

      val result = runSequentiallyAndCollect(inputs)(f).futureValue

      result shouldBe Seq("A", "B", "C", "D")
    }

    "preserve input order even if futures complete out of order" in {
      val inputs = Seq(1, 2, 3, 4)

      def f(i: Int): Future[Int] = Future {
        // smaller i sleeps longer
        Thread.sleep((50 - i * 10).toLong) // 1 = 40ms, 4 = 10ms
        i
      }

      val result = runSequentiallyAndCollect(inputs)(f).futureValue
      result shouldBe inputs
    }

    "return an empty sequence for an empty input iterator" in {
      val inputs: Iterator[Int] = Iterator.empty

      def f(i: Int): Future[Int] = Future.failed(new RuntimeException("should not be called"))

      runSequentiallyAndCollect(inputs)(f).futureValue shouldBe empty
    }

    "should process the iterator lazily, one element at a time" in {
      val totalElements    = 5
      val elementsConsumed = new AtomicInteger(0)

      val inputs = (1 to totalElements).iterator.map { i =>
        elementsConsumed.incrementAndGet()
        i
      }

      def f(i: Int): Future[String] = Future.successful(s"item-$i")

      // start the sequence, only the first element should be pulled immediately
      val future = runSequentiallyAndCollect(inputs)(f)
      elementsConsumed.get() shouldBe 1

      future.futureValue
      elementsConsumed.get() shouldBe totalElements
    }
  }

end FutureUtilsSpec
