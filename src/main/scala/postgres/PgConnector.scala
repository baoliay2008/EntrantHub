package postgres


import slick.jdbc.JdbcBackend.Database

import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }

import util.{ EnvConfig, Logging }


/** Postgres database connector using HikariCP and environment-based configuration.
  *
  * This singleton sets up a Slick `Database` instance backed by a HikariCP connection pool.
  * Configuration is sourced from environment variables via `EnvConfig`.
  */
object PgConnector extends Logging:

  /** This setup is functionally equivalent to configuring Slick via `application.conf`, as shown in
    * [[https://scala-slick.org/doc/stable/database.html#examples Slick documentation]]. Instead, we
    * prefer `forDataSource` with HikariCP and environment variables.
    */
  private val dataSource: HikariDataSource =
    val envConfig = EnvConfig

    // use HikariCP for our connection pool
    val config = HikariConfig()

    // Simple datasource with no connection pooling.
    // The connection pool has already been specified with HikariCP.
    config.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")

    val (serverName, port) = envConfig.getRequired("POSTGRES_ADDRESS").split(":") match
      case Array(s, p) => (s, p)
      case _ =>
        throw IllegalArgumentException(
          "Invalid POSTGRES_ADDRESS format. Expected host:port (e.g. localhost:5432)"
        )
    config.addDataSourceProperty("serverName", serverName)
    config.addDataSourceProperty("portNumber", port)
    config.addDataSourceProperty("databaseName", envConfig.getRequired("POSTGRES_NAME"))
    config.addDataSourceProperty("user", envConfig.getRequired("POSTGRES_USER"))
    config.addDataSourceProperty("password", envConfig.getRequired("POSTGRES_PASSWORD"))

    config.setMaximumPoolSize(envConfig.getRequired("POSTGRES_POOL_THREADS").toInt)
    config.setPoolName("PgHikariCP")

    // Enable JDBC batch rewrite for multi-row inserts
    config.addDataSourceProperty("reWriteBatchedInserts", "true")

    HikariDataSource(config)
  end dataSource

  val db: Database = Database.forDataSource(dataSource, None)
  info("DB pool initialized")

  def shutdown(): Unit =
    info("Attempting to close database connection pool...")
    try
      db.close()
      info("Database connection pool closed successfully.")
    catch
      case e: Exception =>
        // Log the error, but don't re-throw it.
        // We are shutting down anyway, and re-throwing would just print a noisy stack trace.
        error(
          "Error closing the database connection pool. The application will continue to shut down.",
          e,
        )
    end try
  end shutdown

  sys.addShutdownHook(shutdown())

end PgConnector
