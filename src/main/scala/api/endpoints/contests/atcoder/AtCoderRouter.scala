package api.endpoints.contests.atcoder


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import api.common.{ CustomDirectives, Router }


object AtCoderRouter extends Router:

  private val validContestTypes = Set("Algorithm", "Heuristic")

  private def validateContestType(contestType: String): Unit =
    if !validContestTypes.contains(contestType) then
      throw IllegalArgumentException(
        s"Invalid contestType: $contestType. Must be one of: ${validContestTypes.mkString(", ")}"
      )

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("atcoder") {
      contestsRoute ~
        contestByIdRoute
    }

  private def contestsRoute(
    using ExecutionContext
  ): Route =
    path("contests") {
      get {
        parameters("contestType".optional) { contestTypeOpt =>
          contestTypeOpt.foreach(validateContestType)

          CustomDirectives.withPagination() { pagination =>
            debug(
              s"Fetching AtCoder contests: contestType=$contestTypeOpt, " +
                s"limit=${pagination.limit}, offset=${pagination.offset}"
            )
            onComplete(
              AtCoderHandler.getContests(
                contestTypeOpt,
                pagination.limit,
                pagination.offset,
              )
            ) {
              case Success(contests) =>
                complete(contests)
              case Failure(ex) =>
                onInternalError("Fetch AtCoder contests", ex)
            }
          }
        }
      }
    }

  private def contestByIdRoute(
    using ExecutionContext
  ): Route =
    path("contests" / Segment) { id =>
      get {
        debug(s"Fetching AtCoder contest by id=$id")
        onComplete(AtCoderHandler.getContestById(id)) {
          case Success(Some(contest)) =>
            complete(contest)
          case Success(None) =>
            complete(
              StatusCodes.NotFound,
              ErrorResponse(s"Contest with id=$id not found"),
            )
          case Failure(ex) =>
            onInternalError(s"Fetch contest by id=$id", ex)
        }
      }
    }

end AtCoderRouter
