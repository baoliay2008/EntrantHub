package kafka


import scala.concurrent.{ ExecutionContext, Future, Promise }

import org.apache.kafka.clients.producer.{ Callback, KafkaProducer, ProducerRecord, RecordMetadata }
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }

import util.Logging


/** A producer class for sending messages to an MQ system.
  *
  * @tparam K
  *   The type of the message key.
  * @tparam V
  *   The type of the message value.
  *
  * @param topicName
  *   The name of the topic to send messages to. Defaults to "entrant-hub-scala-core-topic".
  * @param keySerializer
  *   The class representing the serializer for the message key. Defaults to [[StringSerializer]].
  * @param valueSerializer
  *   The class representing the serializer for the message value. Defaults to [[StringSerializer]].
  * @param ec
  *   The execution context to use for asynchronous operations.
  */
class MqProducer[K, V](
  val topicName: String = "entrant-hub-scala-core-topic",
  keySerializer: Class[? <: Serializer[K]] = classOf[StringSerializer],
  valueSerializer: Class[? <: Serializer[V]] = classOf[StringSerializer],
)(
  using ec: ExecutionContext
) extends MqClient, Logging:

  private val producerProps = Map(
    "key.serializer"     -> keySerializer.getName,
    "value.serializer"   -> valueSerializer.getName,
    "batch.size"         -> s"${32 * 1024}",
    "linger.ms"          -> "10",
    "compression.type"   -> "gzip",
    "enable.idempotence" -> "true",
    "acks"               -> "all",
  )
  private val producer = KafkaProducer[K, V](createProperties(producerProps))

  /** Sends a single message to the Kafka topic.
    *
    * @param key
    *   the key of the message.
    * @param value
    *   the value of the message.
    * @return
    *   a Future containing the metadata of the record once sent.
    */
  def produce(key: K, value: V): Future[RecordMetadata] =
    val record  = ProducerRecord(topicName, key, value)
    val promise = Promise[RecordMetadata]()
    val callback: Callback = (metadata, exception) =>
      if exception != null then
        error(s"Failed to send message key=$key value=$value", exception)
        promise.failure(exception)
      else
        info(
          s"Message key=$key value=$value sent to topic=${metadata.topic()} partition=${metadata.partition()} offset=${metadata.offset()}"
        )
        promise.success(metadata)
    producer.send(record, callback)
    promise.future
  end produce

  /** Sends multiple messages to the Kafka topic.
    *
    * @param messages
    *   an IterableOnce of key-value pairs to send as messages.
    * @return
    *   a Future that completes when all messages are sent.
    */
  def produce(messages: IterableOnce[(K, V)]): Future[Unit] =
    val it = messages.iterator
    if it.isEmpty then
      warn("No messages to send")
      Future.unit
    else
      Future.traverse(it)(produce(_, _))
        .map(_ => ())
      // TODO: All RecordMetadata are discarded here because I don't need to process them now
  end produce

  /** Shuts down the Kafka producer gracefully, ensuring all resources are released.
    */
  def shutdown(): Unit =
    try
      producer.close()
      info("Producer closed successfully")
    catch
      case e: Exception =>
        error("Error while closing producer", e)

  // Ensure producer closes on JVM shutdown
  sys.addShutdownHook(shutdown())

end MqProducer
