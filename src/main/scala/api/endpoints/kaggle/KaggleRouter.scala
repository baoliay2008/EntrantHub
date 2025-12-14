package api.endpoints.kaggle


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import api.common.{ CustomDirectives, Router }


object KaggleRouter
    extends Router:

  private val validSortFields = Set(
    "deadline",
    "dateEnabled",
    "title",
    "totalCompetitors",
    "totalSubmissions",
  )
  private val validSortOrders = Set("asc", "desc")

  /** Validates sortBy parameter */
  private def validateSortBy(sortBy: String): Unit =
    if !validSortFields.contains(sortBy) then
      throw IllegalArgumentException(
        s"Invalid sortBy: $sortBy. Must be one of: ${validSortFields.mkString(", ")}"
      )

  /** Validates sortOrder parameter */
  private def validateSortOrder(sortOrder: String): Unit =
    if !validSortOrders.contains(sortOrder) then
      throw IllegalArgumentException(
        s"Invalid sortOrder: $sortOrder. Must be one of: ${validSortOrders.mkString(", ")}"
      )

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("kaggle") {
      competitionsRoute ~
        competitionByIdRoute
    }

  private def competitionsRoute(
    using ExecutionContext
  ): Route =
    path("competitions") {
      get {
        parameters(
          "sortBy".?("deadline"),
          "sortOrder".?("desc"),
        ) { (sortBy, sortOrder) =>
          // Validate parameters
          validateSortBy(sortBy)
          validateSortOrder(sortOrder)

          CustomDirectives.withPagination() { pagination =>
            debug(
              s"Fetching Kaggle competitions: sortBy=$sortBy, sortOrder=$sortOrder, " +
                s"limit=${pagination.limit}, offset=${pagination.offset}"
            )
            onComplete(
              KaggleHandler.getCompetitions(
                sortBy,
                sortOrder,
                pagination.limit,
                pagination.offset,
              )
            ) {
              case Success(competitions) =>
                complete(competitions)
              case Failure(ex) =>
                onInternalError("Fetch Kaggle competitions", ex)
            }
          }
        }
      }
    }

  private def competitionByIdRoute(
    using ExecutionContext
  ): Route =
    path("competitions" / IntNumber) { id =>
      get {
        debug(s"Fetching Kaggle competition by id=$id")
        onComplete(KaggleHandler.getCompetitionById(id)) {
          case Success(Some(competition)) =>
            complete(competition)
          case Success(None) =>
            complete(
              StatusCodes.NotFound,
              ErrorResponse(s"Competition with id=$id not found"),
            )
          case Failure(ex) =>
            onInternalError(s"Fetch competition by id=$id", ex)
        }
      }
    }

end KaggleRouter
