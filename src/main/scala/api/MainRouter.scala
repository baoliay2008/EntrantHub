package api


import scala.concurrent.ExecutionContext

import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import org.apache.pekko.http.cors.scaladsl.CorsRejection
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{ ExceptionHandler, RejectionHandler, Route }
import org.apache.pekko.http.scaladsl.server.{
  MalformedQueryParamRejection,
  MissingQueryParamRejection,
  ValidationRejection,
}

import api.common.Responses.ErrorResponse
import api.common.Router


/** Central route aggregator for all API endpoints
  *
  * Add new route modules here as the application grows
  */
object MainRouter
    extends Router:

  def routes(
    using ExecutionContext
  ): Route =
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        cors(middleware.CorsSupport.settings) {
          pathPrefix("api" / "v1") {
            api.endpoints.gsoc.GsocRouter.routes
              ~ api.endpoints.devpost.DevPostRouter.routes
              ~ api.endpoints.kaggle.KaggleRouter.routes
              ~ api.endpoints.contests.ContestsRouter.routes
          } ~
            healthRoutes ~
            rootRoute
        }
      }
    }

  private def healthRoutes: Route =
    path("health") {
      get {
        debug("Health check endpoint called")
        complete("Healthy")
      }
    }

  private def rootRoute: Route =
    pathEndOrSingleSlash {
      get {
        complete("OK")
      }
    }

  // Custom rejection handler
  private val rejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case CorsRejection(cause) =>
        debug(s"CORS rejection: ${cause.description}")
        complete(
          StatusCodes.Forbidden,
          ErrorResponse("CORS: origin not allowed"),
        )
      case MissingQueryParamRejection(paramName) =>
        complete(
          StatusCodes.BadRequest,
          ErrorResponse(s"Missing required query parameter: $paramName"),
        )
      case ValidationRejection(msg, _) =>
        complete(
          StatusCodes.BadRequest,
          ErrorResponse(s"Validation failed: $msg"),
        )
      case MalformedQueryParamRejection(paramName, errorMsg, _) =>
        complete(
          StatusCodes.BadRequest,
          ErrorResponse(s"Invalid parameter '$paramName': $errorMsg"),
        )
    }
    .handleNotFound {
      complete(
        StatusCodes.NotFound,
        ErrorResponse("The requested resource could not be found"),
      )
    }
    .result()

  // Custom exception handler
  private val exceptionHandler = ExceptionHandler {
    case ex: IllegalArgumentException =>
      warn(s"Bad request: ${ex.getMessage}")
      complete(
        StatusCodes.BadRequest,
        ErrorResponse(s"Invalid input: ${ex.getMessage}"),
      )
    case ex: Exception =>
      error("Internal server error", ex)
      complete(
        StatusCodes.InternalServerError,
        ErrorResponse("An unexpected error occurred"),
      )
  }

end MainRouter
