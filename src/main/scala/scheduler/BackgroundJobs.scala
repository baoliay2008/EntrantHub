package scheduler


import java.time.temporal.TemporalAdjusters
import java.time.{ DayOfWeek, Duration as JavaDuration, ZoneId, ZonedDateTime }
import java.util.concurrent.{ Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit }

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }

import util.Logging


/** ===Background Job Scheduler===
  *
  * This utility provides flexible scheduling for asynchronous jobs using a single-threaded
  * `ScheduledExecutorService`. It supports recurring tasks (e.g. hourly, daily, weekly) and one-off
  * tasks, while ensuring the scheduler thread remains unblocked and efficient.
  *
  * ===Threading Model and Non-Blocking Guarantees===
  *
  * Jobs must be provided as `Future[Unit]`, and this design ensures that the scheduler thread is
  * used '''only''' for triggering the jobs—not for executing their contents.
  *
  * Each scheduled job executes a small Runnable that:
  *
  *   1. Evaluates the job block, which returns a `Future[Unit]`
  *   1. Attaches an `.onComplete` callback to log success/failure
  *
  * Because `.onComplete` is non-blocking and merely registers a callback, and because actual job
  * work happens inside the `Future`, the scheduler thread remains free for subsequent executions.
  *
  * ===Important Note on Blocking Code===
  *
  * Ensure job logic is fully asynchronous. For example:
  *
  * DO NOT write blocking code outside the Future:
  * {{{
  * BackgroundJobs.scheduleHourly("myJob") {
  *   blockingOperation() // This blocks the scheduler thread!
  *   Future.unit
  * }
  * }}}
  *
  * INSTEAD, wrap blocking logic inside the Future:
  * {{{
  * BackgroundJobs.scheduleHourly("myJob") {
  *   Future {
  *     blockingOperation() // Runs on ExecutionContext.global
  *   }
  * }
  * }}}
  *
  * If used correctly, the scheduler requires only '''one thread''', and there is no need to
  * increase the thread pool size. Jobs execute in `ExecutionContext.global` or whatever context is
  * used within your `Future`.
  */
object BackgroundJobs extends Logging:

  private given ec: ExecutionContext = ExecutionContext.global

  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val zone: ZoneId                       = ZoneId.of("UTC")

  private def scheduleRecurring(
    jobName: String,
    initialDelayMillis: => Long,
    periodMillis: Long,
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =

    // define the core task logic in a Runnable.
    val runnableJob: Runnable = () =>
      job.onComplete {
        case Success(_) =>
          info(s"Job [$jobName] finished.")
        case Failure(ex) =>
          error(s"Job [$jobName] failed with an exception.", ex)
      }

    // it will run for the first time after 'initialDelayMillis'
    // and then every 'periodMillis' thereafter.
    val scheduled = executor.scheduleAtFixedRate(
      runnableJob,
      initialDelayMillis,
      periodMillis,
      TimeUnit.MILLISECONDS,
    )
    debug(
      s"Job [$jobName] scheduled to run every $periodMillis ms."
    )
    scheduled
  end scheduleRecurring

  def scheduleWeekly(
    dayOfWeek: DayOfWeek,
    hour: Int = 0,
    minute: Int = 0,
    second: Int = 0,
    jobName: String = "weekly job",
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =
    def computeInitialDelay(): Long =
      val now = ZonedDateTime.now(zone)
      var next = now
        .withHour(hour)
        .withMinute(minute)
        .withSecond(second)
        .withNano(0)
        .`with`(TemporalAdjusters.nextOrSame(dayOfWeek))
      if next.isBefore(now) then next = next.plusWeeks(1)
      JavaDuration.between(now, next).toMillis

    scheduleRecurring(jobName, computeInitialDelay(), 7.days.toMillis)(job)
  end scheduleWeekly

  def scheduleDaily(
    hour: Int = 0,
    minute: Int = 0,
    second: Int = 0,
    jobName: String = "daily job",
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =
    def computeInitialDelay(): Long =
      val now = ZonedDateTime.now(zone)
      var next = now
        .withHour(hour)
        .withMinute(minute)
        .withSecond(second)
        .withNano(0)
      if next.isBefore(now) then next = next.plusDays(1)
      JavaDuration.between(now, next).toMillis

    scheduleRecurring(jobName, computeInitialDelay(), 1.days.toMillis)(job)
  end scheduleDaily

  def scheduleHourly(
    minute: Int = 0,
    second: Int = 0,
    jobName: String = "hourly job",
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =
    def computeInitialDelay(): Long =
      val now = ZonedDateTime.now(zone)
      var next = now
        .withMinute(minute)
        .withSecond(second)
        .withNano(0)
      if next.isBefore(now) then next = next.plusHours(1)
      JavaDuration.between(now, next).toMillis

    scheduleRecurring(jobName, computeInitialDelay(), 1.hours.toMillis)(job)
  end scheduleHourly

  def scheduleMinutely(
    second: Int = 0,
    jobName: String = "per-minute job",
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =
    def computeInitialDelay(): Long =
      val now = ZonedDateTime.now(zone)
      var next = now
        .withSecond(second)
        .withNano(0)
      if next.isBefore(now) then next = next.plusMinutes(1)
      JavaDuration.between(now, next).toMillis

    scheduleRecurring(jobName, computeInitialDelay(), 1.minutes.toMillis)(job)
  end scheduleMinutely

  def scheduleEveryNSeconds(
    n: Int,
    initialDelaySeconds: Int = 0,
    jobName: String = "per-N-seconds job",
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =
    require(n > 0, "n must be positive")
    require(initialDelaySeconds >= 0, "initialDelaySeconds must be non-negative")

    scheduleRecurring(jobName, initialDelaySeconds * 1000L, n.seconds.toMillis)(job)
  end scheduleEveryNSeconds

  def scheduleOnce(
    delay: FiniteDuration,
    jobName: String = "one-time job",
  )(
    job: => Future[Unit]
  ): ScheduledFuture[?] =
    val runnableJob: Runnable = () =>
      job.onComplete {
        case Success(_) =>
          info(s"One-time job [$jobName] finished successfully.")
        case Failure(ex) =>
          error(s"One-time job [$jobName] failed with an exception.", ex)
      }

    val scheduled = executor.schedule(
      runnableJob,
      delay.toMillis,
      TimeUnit.MILLISECONDS,
    )
    debug(
      s"One-time job [$jobName] scheduled to run once after a delay of ${delay.toString}."
    )
    scheduled
  end scheduleOnce

  def waitFor(duration: FiniteDuration): Future[Unit] =
    val promise       = Promise[Unit]()
    val job: Runnable = () => promise.success(())
    executor.schedule(
      job,
      duration.toMillis,
      TimeUnit.MILLISECONDS,
    )
    promise.future

  /** Generic polling helper: retries a boolean condition until true, with a fixed delay between
    * attempts. Executes `onSuccess` if the condition succeeds, or `onFailure` if max retries are
    * exceeded.
    *
    * @param condition
    *   A Future[Boolean] that completes with true when the desired state is reached
    * @param retryDelay
    *   Delay between retries
    * @param maxRetries
    *   Optional maximum number of attempts (None = infinite)
    * @param onSuccess
    *   Logic to run if condition eventually succeeds
    * @param onFailure
    *   Logic to run if retries are exhausted (default: logs and does nothing)
    * @param jobName
    *   Name for logging and identification
    * @return
    *   Future[Unit] completing when onSuccess or onFailure has been executed
    */
  def pollUntil(
    condition: => Future[Boolean],
    retryDelay: FiniteDuration,
    maxRetries: Option[Int] = None,
    onSuccess: => Future[Unit],
    onFailure: => Future[Unit] = Future.unit,
    jobName: String = "pollUntil",
  ): Future[Unit] =

    def attempt(count: Int): Future[Unit] =
      condition.recover {
        case e =>
          error(s"Poll job [$jobName] condition failed with exception on attempt [$count]", e)
          false // Treat exceptions as false to continue retrying
      }.flatMap {
        case true =>
          info(s"Poll job [$jobName] succeeded on attempt [$count]. Running success logic.")
          onSuccess
        case false if maxRetries.exists(count >= _) =>
          error(s"Poll job [$jobName] exceeded max retries [$count]. Running failure logic.")
          onFailure
        case false =>
          warn(s"Poll job [$jobName] attempt [$count] not ready; retrying in $retryDelay.")
          // Chain the delay and the next attempt
          waitFor(retryDelay).flatMap(_ => attempt(count + 1))
      }
    end attempt

    attempt(1)
  end pollUntil

  /** Forcibly shuts down the scheduler executor service.
    *
    * This calls `shutdownNow()`, which attempts to interrupt any running task and discards all
    * queued tasks. It does not wait for tasks to complete. This is a fast but ungraceful way to
    * ensure the scheduler does not prevent the application from exiting.
    */
  def shutdown(): Unit =
    info("Forcibly shutting down the scheduler...")
    try
      val droppedTasks = executor.shutdownNow()
      if !droppedTasks.isEmpty then
        warn(s"${droppedTasks.size} tasks were awaiting execution and have been dropped.")
      info("Scheduler shutdown complete.")
    catch
      case e: Exception =>
        error("An unexpected error occurred during forceful scheduler shutdown.", e)
  end shutdown

  // Ensure scheduler closes on JVM shutdown
  sys.addShutdownHook(shutdown())

end BackgroundJobs
