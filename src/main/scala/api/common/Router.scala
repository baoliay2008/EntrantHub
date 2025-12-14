package api.common


import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import util.Logging


trait Router
    extends JsonMarshalling, Logging:

  /** Handles internal server errors with consistent logging and user-facing messages.
    *
    * @param context
    *   description of the operation that failed (e.g., "Fetch organizations")
    * @param ex
    *   the exception that occurred
    * @return
    *   a Route completing with 500 status and generic error message
    */
  protected def onInternalError(context: String, ex: Throwable): Route =
    error(s"$context failed", ex)
    complete(
      StatusCodes.InternalServerError,
      ErrorResponse("An unexpected error occurred"),
    )

end Router
