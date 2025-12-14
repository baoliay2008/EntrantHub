package api.middleware


import org.apache.pekko.http.cors.scaladsl.model.{ HttpHeaderRange, HttpOriginMatcher }
import org.apache.pekko.http.cors.scaladsl.settings.CorsSettings
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.headers.HttpOrigin

import util.{ EnvConfig, Logging }


object CorsSupport
    extends Logging:

  lazy val settings: CorsSettings =
    val origins = EnvConfig
      .getRequired("ALLOWED_ORIGINS")
      .split(",")
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSeq
    info(s"CORS allowed origins: ${origins.mkString(", ")}")

    val allowedOrigins = HttpOriginMatcher(
      origins.map(HttpOrigin(_))*
    )

    CorsSettings
      .defaultSettings
      .withAllowedOrigins(allowedOrigins)
      .withAllowedMethods(
        Seq(
          HttpMethods.OPTIONS,
          HttpMethods.GET,
          HttpMethods.POST,
          HttpMethods.PATCH,
          HttpMethods.DELETE,
        )
      )
      .withMaxAge(Some(3600L))
      .withAllowCredentials(true)
      .withAllowedHeaders(
        HttpHeaderRange(
          "Content-Type",
          "Authorization",
          "X-Api-Key",
          "X-Request-ID",
        )
      )
      .withExposedHeaders(
        Seq(
          "X-Rate-Limit-Remaining",
          "X-Rate-Limit-Reset",
          "X-Request-ID",
        )
      )
  end settings

end CorsSupport
