package kafka


import java.time.Duration

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContext, Future, Promise, blocking }
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import org.apache.kafka.clients.consumer.{ ConsumerRecord, KafkaConsumer }
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.{ Deserializer, StringDeserializer }

import util.Logging


/** A consumer class for consuming messages from an MQ system.
  *
  * @tparam K
  *   The type of the message key.
  * @tparam V
  *   The type of the message value.
  *
  * @param topicNames
  *   The '''LIST''' of topic names to subscribe to. Defaults to a single topic,
  *   "entrant-hub-scala-core-topic".
  * @param groupId
  *   The consumer group ID. Defaults to "scala-core-consumer-group".
  * @param offsetReset
  *   The offset reset policy, either "earliest" or "latest". Defaults to "earliest".
  * @param keyDeserializer
  *   The class representing the deserializer for the message key. Defaults to
  *   [[StringDeserializer]].
  * @param valueDeserializer
  *   The class representing the deserializer for the message value. Defaults to
  *   [[StringDeserializer]].
  * @param timeout
  *   The poll timeout duration. Defaults to 1 second.
  * @param ec
  *   The execution context to use for asynchronous operations.
  */
class MqConsumer[K, V](
  val topicNames: List[String] = "entrant-hub-scala-core-topic" :: Nil,
  val groupId: String = "scala-core-consumer-group",
  offsetReset: String = "earliest",
  keyDeserializer: Class[? <: Deserializer[K]] = classOf[StringDeserializer],
  valueDeserializer: Class[? <: Deserializer[V]] = classOf[StringDeserializer],
  timeout: Duration = Duration.ofMillis(1_000),
)(
  using ec: ExecutionContext
) extends MqClient, Logging:

  private val consumerProps = Map(
    "key.deserializer"   -> keyDeserializer.getName,
    "value.deserializer" -> valueDeserializer.getName,
    "group.id"           -> groupId,
    "auto.offset.reset"  -> offsetReset,
  )
  private val consumer = KafkaConsumer[K, V](createProperties(consumerProps))

  @volatile private var isRunning = true
  // Block the close() method until the consumer loop has exited
  private val shutdownPromise = Promise[Unit]()

  // Automatically subscribe to the default topics
  if topicNames.isEmpty then throw IllegalArgumentException("Default topics cannot be empty")
  else consumer.subscribe(topicNames.asJava)

  /** Starts consuming messages from the Kafka topics and processes them using the provided
    * callback.
    *
    * @param onMessage
    *   a callback function to process the consumed records.
    * @return
    *   a Future that completes when the consumer stops.
    */
  def consume(onMessage: ConsumerRecord[K, V] => Unit): Future[Unit] =
    Future {
      try
        while isRunning do
          val records = blocking(consumer.poll(timeout))
          records.iterator().asScala.foreach { record =>
            Future {
              try onMessage(record)
              catch
                case NonFatal(e) =>
                  error(s"Error while processing message: $record", e)
            }
          }
      catch
        case _: WakeupException if !isRunning =>
          info("Consumer loop closing normally")
        case NonFatal(e) =>
          error("Unexpected error in consumer loop", e)
      finally
        try consumer.close()
        catch
          case NonFatal(e) =>
            error("Error during consumer close", e)
        shutdownPromise.trySuccess(()) // Mark the shutdown process as complete
    }
  end consume

  /** Shuts down the consumer gracefully, ensuring all resources are released.
    */
  def shutdown(): Unit =
    if isRunning then
      isRunning = false
      consumer.wakeup()
      try
        // Wait for the shutdownPromise to complete
        Await.result(shutdownPromise.future, 10.seconds)
        info("Consumer closed successfully")
      catch
        case e: Exception =>
          error("Error waiting for consumer shutdown", e)
  end shutdown

  // Ensure consumer closes on JVM shutdown
  sys.addShutdownHook(shutdown())

end MqConsumer
