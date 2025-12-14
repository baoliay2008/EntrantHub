package util

import scala.concurrent.{ ExecutionContext, Future }


object FutureUtils:

  /** Processes the provided inputs in batches for their side effects only, discarding any results.
    * This method is optimized for cases where the return values of `f` are not needed, such as
    * logging, saving to a database, or other fire-and-forget operations.
    *
    * Each batch is processed sequentially, but inputs within a batch are processed concurrently.
    *
    * It avoids unnecessary memory overhead and should be preferred over `runBatchedAndCollect` when
    * dealing with a large number of elements.
    *
    * @param inputs
    *   An iterator of input elements to process.
    * @param batchSize
    *   The number of elements to process concurrently in each batch.
    * @param f
    *   A side-effecting function that returns `Future[Unit]` for each input.
    * @param ec
    *   The execution context used for concurrent execution.
    * @tparam A
    *   The type of input elements.
    * @return
    *   A `Future[Unit]` that completes when all batches have been processed.
    */
  def runInBatchesAndDiscard[A](
    inputs: IterableOnce[A],
    batchSize: Int = 10,
  )(f: A => Future[Unit])(
    using ec: ExecutionContext
  ): Future[Unit] =
    val batches = inputs.iterator.grouped(batchSize)

    def loop(): Future[Unit] =
      if batches.hasNext then
        val batch = batches.next()
        Future.traverse(batch)(f).flatMap(_ => loop())
      else
        Future.unit

    loop()
  end runInBatchesAndDiscard

  /** Processes the provided inputs in batches and accumulates the results of applying the function
    * `f` to each input.
    *
    * Each batch is processed sequentially, but inputs within a batch are processed concurrently.
    *
    * This method is suitable when you need to collect and retain the results.
    *
    * @param inputs
    *   An iterator over the inputs to process.
    * @param batchSize
    *   The number of elements to process concurrently in each batch.
    * @param f
    *   The function to apply to each input. The function must return a `Future` of type `B` for
    *   each input of type `A`.
    * @param ec
    *   The execution context used for concurrent execution.
    * @tparam A
    *   The type of input elements.
    * @tparam B
    *   The type of output elements.
    * @return
    *   A `Future` containing a sequence of results (`Seq[B]`), where each result corresponds to the
    *   application of `f` to the inputs.
    */
  def runInBatchesAndCollect[A, B](
    inputs: IterableOnce[A],
    batchSize: Int = 10,
  )(
    f: A => Future[B]
  )(
    using ec: ExecutionContext
  ): Future[Seq[B]] =
    val batches = inputs.iterator.grouped(batchSize)
    val buffer  = scala.collection.mutable.ArrayBuffer.empty[B]

    def loop(): Future[Seq[B]] =
      if batches.hasNext then
        val batch = batches.next()
        Future.traverse(batch)(f).flatMap { results =>
          buffer ++= results
          loop()
        }
      else Future.successful(buffer.toSeq)

    loop()
  end runInBatchesAndCollect

  /** Processes the provided inputs one at a time in strict sequential order, discarding any
    * results.
    *
    * This method ensures that each future-producing operation completes before the next one begins.
    * It is ideal for workflows that must be executed in sequence and where results do not need to
    * be retained.
    *
    * @param inputs
    *   An iterator of input elements to process.
    * @param f
    *   A side-effecting function that returns `Future[Unit]` for each input.
    * @param ec
    *   The execution context used for running asynchronous computations.
    * @tparam A
    *   The type of input elements.
    * @return
    *   A `Future[Unit]` that completes when all inputs have been processed in order.
    */
  def runSequentiallyAndDiscard[A](
    inputs: IterableOnce[A]
  )(f: A => Future[Unit])(
    using ec: ExecutionContext
  ): Future[Unit] =
    val it = inputs.iterator

    def loop(): Future[Unit] =
      if it.hasNext then
        f(it.next()).flatMap(_ => loop())
      else
        Future.unit

    loop()
  end runSequentiallyAndDiscard

  /** Processes the provided inputs one at a time in strict sequential order and collects the
    * results.
    *
    * This method ensures that each future-producing operation completes before the next one begins,
    * accumulating the results into a sequence. It is suitable when operations must be ordered and
    * their results preserved.
    *
    * @param inputs
    *   An iterator of input elements to process.
    * @param f
    *   A function that returns a `Future[B]` for each input.
    * @param ec
    *   The execution context used for running asynchronous computations.
    * @tparam A
    *   The type of input elements.
    * @tparam B
    *   The type of result elements.
    * @return
    *   A `Future[Seq[B]]` containing the results of applying `f` to each input in order.
    */
  def runSequentiallyAndCollect[A, B](
    inputs: IterableOnce[A]
  )(f: A => Future[B])(
    using ec: ExecutionContext
  ): Future[Seq[B]] =
    val it     = inputs.iterator
    val buffer = scala.collection.mutable.ArrayBuffer.empty[B]

    def loop(): Future[Seq[B]] =
      if it.hasNext then
        f(it.next()).flatMap { b =>
          buffer += b
          loop()
        }
      else
        Future.successful(buffer.toSeq)

    loop()
  end runSequentiallyAndCollect

end FutureUtils
