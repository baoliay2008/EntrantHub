package kafka


import java.util.Properties

import util.EnvConfig


/** Trait representing a Kafka client. Provides utility methods to create and configure properties
  * required for connecting to Kafka.
  */
trait MqClient:

  /** Creates a set of Kafka connection properties.
    *
    * @param additionalProps
    *   A map of additional properties that can be provided by the caller to customize the
    *   configuration.
    * @return
    *   A `Properties` object containing the combined Kafka connection properties.
    *
    * The method does the following:
    *   - Fetches required Kafka configuration values (e.g., brokers, client ID, security settings)
    *     from the environment using `EnvConfig`.
    *   - Constructs the mandatory Kafka configuration properties, including authentication details.
    *   - Merges user-provided custom properties (`additionalProps`) into the Kafka properties.
    */
  protected def createProperties(additionalProps: Map[String, String] = Map.empty): Properties =
    val props     = Properties()
    val envConfig = EnvConfig

    // Add required properties
    props.put("bootstrap.servers", envConfig.getRequired("KAFKA_BROKER"))
    props.put("client.id", envConfig.getRequired("KAFKA_CLIENT_ID"))
    props.put("security.protocol", envConfig.getRequired("KAFKA_SECURITY_PROTOCOL"))
    props.put("sasl.mechanism", envConfig.getRequired("KAFKA_SASL_MECHANISM"))
    props.put(
      "sasl.jaas.config",
      envConfig.getRequired("KAFKA_SASL_MECHANISM") match
        case "PLAIN" =>
          s"""org.apache.kafka.common.security.plain.PlainLoginModule required
             |username="${envConfig.getRequired("KAFKA_USERNAME")}"
             |password="${envConfig.getRequired("KAFKA_PASSWORD")}";""".stripMargin
        case "SCRAM-SHA-256" | "SCRAM-SHA-512" =>
          s"""org.apache.kafka.common.security.scram.ScramLoginModule required
             |username="${envConfig.getRequired("KAFKA_USERNAME")}"
             |password="${envConfig.getRequired("KAFKA_PASSWORD")}";""".stripMargin
        case other =>
          throw IllegalArgumentException(s"Unsupported SASL mechanism: $other"),
    )

    // Add any user-provided custom properties
    for (key, value) <- additionalProps do props.put(key, value)

    props

  end createProperties

end MqClient
