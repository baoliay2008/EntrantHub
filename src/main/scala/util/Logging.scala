package util

import org.slf4j.{ Logger as Slf4jLogger, LoggerFactory }


/** A trait that provides logging functionality using the SLF4J logging framework.
  *
  * The `Logging` trait enables a class to log messages at various levels (trace, debug, info, warn,
  * and error). It defines methods for logging messages as well as logging messages with exceptions.
  *
  * Note: The message argument is a by-name parameter (`=> String`), allowing for deferred
  * evaluation of the string in case the logging level is disabled, improving performance when
  * logging is disabled at runtime.
  */
transparent trait Logging:
  private val logger: Slf4jLogger = LoggerFactory.getLogger(getClass)

  def trace(message: => String): Unit =
    if logger.isTraceEnabled then logger.trace(message)

  def trace(message: => String, exception: Throwable): Unit =
    if logger.isTraceEnabled then logger.trace(message, exception)

  def debug(message: => String): Unit =
    if logger.isDebugEnabled then logger.debug(message)

  def debug(message: => String, exception: Throwable): Unit =
    if logger.isDebugEnabled then logger.debug(message, exception)

  def info(message: => String): Unit =
    if logger.isInfoEnabled then logger.info(message)

  def info(message: => String, exception: Throwable): Unit =
    if logger.isInfoEnabled then logger.info(message, exception)

  def warn(message: => String): Unit =
    if logger.isWarnEnabled then logger.warn(message)

  def warn(message: => String, exception: Throwable): Unit =
    if logger.isWarnEnabled then logger.warn(message, exception)

  def error(message: => String): Unit =
    if logger.isErrorEnabled then logger.error(message)

  def error(message: => String, exception: Throwable): Unit =
    if logger.isErrorEnabled then logger.error(message, exception)

  // This is for synchronous operations only. For Futures, this will log "completed" immediately
  // when the Future is created, not when it actually completes. To properly support Futures,
  // write another method with `inline body: => Future[B]` that handles async completion.
  inline def withLogging[B](label: String)(inline body: => B): B =
    info(s"<$label> :: started")
    try
      val result = body
      info(s"<$label> :: completed")
      result
    catch
      case e: Exception =>
        error(s"<$label> :: failed | error='${e.getMessage}'", e)
        throw e
  end withLogging

  // This is for synchronous operations only. For Futures, this will log "completed" immediately
  // when the Future is created, not when it actually completes. To properly support Futures,
  // write another method with `inline body: => Future[B]` that handles async completion.
  inline def withLoggingAndTimer[B](label: String)(inline body: => B): B =
    info(s"<$label> :: started")
    val startTime = System.nanoTime()
    try
      val result    = body
      val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
      info(s"<$label> :: completed | duration=${elapsedMs}ms")
      result
    catch
      case e: Exception =>
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        error(s"<$label> :: failed | duration=${elapsedMs}ms | error='${e.getMessage}'", e)
        throw e
  end withLoggingAndTimer

  extension [A](a: A)
    inline def \\(inline label: String): A =
      info(s"TAP <$label> :: $a")
      a

end Logging
