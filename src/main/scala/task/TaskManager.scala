package task


import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

import upickle.default.{ ReadWriter, read, write }

import kafka.{ MqConsumer, MqProducer }
import scheduler.BackgroundJobs
import util.Logging


/** A global singleton that manages tasks sent to and received from message queues. It maintains a
  * hashmap with unique IDs to track pending tasks.
  *
  * Currently, a singleton instance is sufficient, but this may be refactored into a class in the
  * future for greater flexibility.
  */
object TaskManager extends Logging:
  private given ec: ExecutionContext = ExecutionContext.global

  // Single producer and consumer for ALL tasks.
  // In the future, this could be refactored to use multiple producers and consumers
  // for different types of tasks if needed.
  private val producer =
    MqProducer[String, String](topicName = "entrant-hub-http-request-tasks")
  private val consumer =
    MqConsumer[String, String](topicNames = List("entrant-hub-http-request-results"))

  // make task fail after this limit is reached
  private val TimeoutLimit: FiniteDuration = 20.seconds

  // A buffer(concurrent map) to track pending requests by their correlation ID.
  private val pendingRequests = ConcurrentHashMap[String, Promise[String]]()

  // Start the consumer loop (should be started once at startup)
  consumer.consume(r =>
    val correlationId = r.key()
    val promise       = pendingRequests.remove(correlationId)
    if promise != null then
      trace(s"Received response for task [$correlationId]. Completing promise.")
      promise.trySuccess(r.value())
    else
      warn(s"Received response for unknown or timed-out task [$correlationId]. Discarding.")
  )

  /** Dispatches a task asynchronously and returns a Future with the response.
    * @param task
    *   The task to be serialized and sent.
    * @tparam Payload
    *   The type of the task payload, which must have a `ReadWriter` instance for serialization.
    * @tparam Result
    *   The type of the result content, which must have a `ReadWriter` instance for serialization.
    * @return
    *   A Future containing the response as a String.
    * @throws RuntimeException
    *   if the task processing fails.
    */
  def dispatchTaskAsync[Payload: ReadWriter, Result: ReadWriter](
    task: Task[Payload]
  ): Future[TaskResult[Result]] =
    val correlationId   = UUID.randomUUID().toString
    val responsePromise = Promise[String]()
    // Store the promise keyed by the correlation ID.
    // IMPORTANT: remove promise from pendingRequests to avoid memory leak!
    // The winner of the race (consumer/timeout/producer-failure) BOTH removes AND completes atomically.
    pendingRequests.put(correlationId, responsePromise)
    debug(s"Dispatching task [$correlationId] with ${task.retries} retries left.")

    // Schedule timeout job using BackgroundJobs
    val timeoutJob = BackgroundJobs.scheduleOnce(TimeoutLimit, s"timeout-task-$correlationId") {
      Future {
        // Remove from map BEFORE failing the promise to prevent race conditions
        val promise = pendingRequests.remove(correlationId)
        if promise != null then
          promise.tryFailure(
            RuntimeException(s"task [$correlationId] timed out after $TimeoutLimit")
          )
        ()
      }
    }
    // Ensure the timeout is cancelled as soon as the promise completes for ANY reason.
    // This handles the success case (Kafka response) and the producer failure case.
    responsePromise.future.onComplete { _ =>
      timeoutJob.cancel(false)
    }

    val message = write(task)
    // Send the request asynchronously
    producer.produce(correlationId, message).onComplete {
      case Success(_) => // Do nothing
      case Failure(e) =>
        val promise = pendingRequests.remove(correlationId)
        if promise != null then
          promise.tryFailure(
            RuntimeException(s"Producer cannot send task [$correlationId] ${e.getMessage}", e)
          )
    }

    // Return the future that resolves when the response is received
    responsePromise.future
      .map { responseJson =>
        try
          read[TaskResult[Result]](responseJson)
        catch
          case e: Throwable =>
            throw RuntimeException("Failed to parse response JSON, probably a format error", e)
      }
      .recoverWith {
        case NonFatal(e) if task.retries > 0 =>
          val nextAttempt = task.copy(retries = task.retries - 1)
          dispatchTaskAsync(nextAttempt)
        case e =>
          Future.failed(
            RuntimeException(s"task [$correlationId] final failure ${e.getMessage}", e)
          )
      }
  end dispatchTaskAsync

  def shutdown(): Unit =
    info(s"Shutting down TaskManager. ${pendingRequests.size} tasks pending.")
    // Fail all pending tasks
    pendingRequests.forEach { (correlationId, promise) =>
      promise.tryFailure(
        RuntimeException(s"TaskManager shutting down $correlationId")
      )
    }
    pendingRequests.clear()

  // Ensure all pending requests close on JVM shutdown
  sys.addShutdownHook(shutdown())

end TaskManager
