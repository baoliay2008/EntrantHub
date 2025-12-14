package api.common


import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives.*

import api.common.Responses.ErrorResponse


case class PaginationParams(limit: Int, offset: Int)


object CustomDirectives
    extends JsonMarshalling:

  /** Validates and extracts pagination parameters from query string.
    *
    * Provides default values of limit=20 and offset=0 when parameters are not specified. Validates
    * that limit is positive, within the maximum allowed range, and offset is non-negative.
    *
    * @param maxLimit
    *   the maximum allowed value for the limit parameter (default: 100)
    * @return
    *   a directive that extracts validated PaginationParams or completes with BadRequest if
    *   validation fails
    */
  def withPagination(maxLimit: Int = 100): Directive1[PaginationParams] =
    parameters("limit".as[Int].?(20), "offset".as[Int].?(0))
      .tflatMap { (limit, offset) =>
        if limit < 1 then
          complete(StatusCodes.BadRequest, ErrorResponse("Limit must be positive"))
        else if limit > maxLimit then
          complete(StatusCodes.BadRequest, ErrorResponse(s"Limit cannot exceed $maxLimit"))
        else if offset < 0 then
          complete(StatusCodes.BadRequest, ErrorResponse("Offset cannot be negative"))
        else
          provide(PaginationParams(limit, offset))
      }

end CustomDirectives
