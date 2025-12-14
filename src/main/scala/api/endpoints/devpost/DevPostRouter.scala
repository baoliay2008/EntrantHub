package api.endpoints.devpost


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import api.common.{ CustomDirectives, Router }


object DevPostRouter
    extends Router:

  private val validOpenStates = Set("open", "upcoming", "ended")
  private val validSortFields = Set(
    "submissionEndDate",
    "submissionStartDate",
    "registrationsCount",
    "title",
  )
  private val validSortOrders = Set("asc", "desc")

  /** Validates openState parameter */
  private def validateOpenState(openState: String): Unit =
    if !validOpenStates.contains(openState) then
      throw IllegalArgumentException(
        s"Invalid openState: $openState. Must be one of: ${validOpenStates.mkString(", ")}"
      )

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
    pathPrefix("devpost") {
      categoriesRoute ~
        hackathonsRoute ~
        hackathonByIdRoute
    }

  private def categoriesRoute(
    using ExecutionContext
  ): Route =
    path("categories") {
      get {
        debug("Fetching DevPost categories")
        onComplete(DevPostHandler.getCategories()) {
          case Success(categories) =>
            complete(categories)
          case Failure(ex) =>
            onInternalError("Fetch DevPost categories", ex)
        }
      }
    }

  private def hackathonsRoute(
    using ExecutionContext
  ): Route =
    path("hackathons") {
      get {
        parameters(
          "categoryId".as[Int].?,
          "openState".?,
          "sortBy".?("submissionEndDate"),
          "sortOrder".?("desc"),
        ) { (categoryIdOpt, openStateOpt, sortBy, sortOrder) =>
          // Validate parameters
          openStateOpt.foreach(validateOpenState)
          validateSortBy(sortBy)
          validateSortOrder(sortOrder)

          CustomDirectives.withPagination() { pagination =>
            debug(
              s"Fetching DevPost hackathons: categoryId=$categoryIdOpt, " +
                s"openState=$openStateOpt, sortBy=$sortBy, sortOrder=$sortOrder, " +
                s"limit=${pagination.limit}, offset=${pagination.offset}"
            )
            onComplete(
              DevPostHandler.getHackathons(
                categoryIdOpt,
                openStateOpt,
                sortBy,
                sortOrder,
                pagination.limit,
                pagination.offset,
              )
            ) {
              case Success(hackathons) =>
                complete(hackathons)
              case Failure(ex) =>
                onInternalError(
                  s"Fetch hackathons for categoryId=$categoryIdOpt, openState=$openStateOpt",
                  ex,
                )
            }
          }
        }
      }
    }

  private def hackathonByIdRoute(
    using ExecutionContext
  ): Route =
    path("hackathons" / IntNumber) { id =>
      get {
        debug(s"Fetching DevPost hackathon by id=$id")
        onComplete(DevPostHandler.getHackathonById(id)) {
          case Success(Some(hackathon)) =>
            complete(hackathon)
          case Success(None) =>
            complete(
              StatusCodes.NotFound,
              ErrorResponse(s"Hackathon with id=$id not found"),
            )
          case Failure(ex) =>
            onInternalError(s"Fetch hackathon by id=$id", ex)
        }
      }
    }

end DevPostRouter
