package util


import scala.jdk.CollectionConverters.*

import io.github.cdimascio.dotenv.Dotenv

import util.Extensions.getOrThrow


/** A utility object for accessing environment variables using the dotenv-java library.
  */
object EnvConfig:

  // The Dotenv instance used to load and access environment variables.
  private lazy val dotenv: Dotenv = Dotenv.load()

  /** Retrieves the value of an environment variable by its name.
    *
    * @param key
    *   The name of the environment variable to retrieve.
    * @return
    *   An `Option[String]` containing the value if the variable exists, or `None` if it does not.
    */
  def get(key: String): Option[String] =
    Option(dotenv.get(key))

  /** Retrieves the value of an environment variable by its name, returning a default value if the
    * variable is not found.
    *
    * @param key
    *   The name of the environment variable to retrieve.
    * @param defaultValue
    *   The default value to return if the variable is not found.
    * @return
    *   A `String` containing either the variable's value or the provided default value.
    */
  def getOrElse(key: String, defaultValue: String): String =
    dotenv.get(key, defaultValue)

  /** Retrieves the value of a required environment variable by its name.
    *
    * @param key
    *   The name of the environment variable to retrieve.
    * @return
    *   A `String` containing the value of the environment variable.
    * @throws NoSuchElementException
    *   if the variable is not found.
    */
  def getRequired(key: String): String =
    get(key)
      .getOrThrow(s"Environment variable '$key' not found.")

  /** Loads all environment variables into a `Map[String, String]`.
    *
    * @return
    *   A `Map` where keys are the names of the environment variables and values are their
    *   corresponding values.
    */
  def getAll: Map[String, String] =
    dotenv
      .entries()
      .asScala
      .map(entry => entry.getKey -> entry.getValue)
      .toMap

end EnvConfig
