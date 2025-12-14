package api.endpoints.contests.codechef


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import api.common.{ CustomDirectives, Router }


object CodeChefRouter extends Router:

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("codechef") {
      contestsRoute ~
        contestByIdRoute
    }

  private def contestsRoute(
    using ExecutionContext
  ): Route =
    path("contests") {
      get {
        CustomDirectives.withPagination() { pagination =>
          debug(
            s"Fetching CodeChef contests: " +
              s"limit=${pagination.limit}, offset=${pagination.offset}"
          )
          onComplete(
            CodeChefHandler.getContests(
              pagination.limit,
              pagination.offset,
            )
          ) {
            case Success(contests) =>
              complete(contests)
            case Failure(ex) =>
              onInternalError("Fetch CodeChef contests", ex)
          }
        }
      }
    }

  private def contestByIdRoute(
    using ExecutionContext
  ): Route =
    path("contests" / Segment) { id =>
      get {
        debug(s"Fetching CodeChef contest by id=$id")
        onComplete(CodeChefHandler.getContestById(id)) {
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

end CodeChefRouter
