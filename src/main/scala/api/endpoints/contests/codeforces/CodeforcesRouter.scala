package api.endpoints.contests.codeforces


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import api.common.{ CustomDirectives, Router }


object CodeforcesRouter extends Router:

  private val validPhases =
    Set("BEFORE", "CODING", "PENDING_SYSTEM_TEST", "SYSTEM_TEST", "FINISHED")

  private def validatePhase(phase: String): Unit =
    if !validPhases.contains(phase) then
      throw IllegalArgumentException(
        s"Invalid phase: $phase. Must be one of: ${validPhases.mkString(", ")}"
      )

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("codeforces") {
      contestsRoute ~
        contestByIdRoute
    }

  private def contestsRoute(
    using ExecutionContext
  ): Route =
    path("contests") {
      get {
        parameters("phase".optional, "includeGym".as[Boolean].?(false)) { (phaseOpt, includeGym) =>
          phaseOpt.foreach(validatePhase)

          CustomDirectives.withPagination() { pagination =>
            debug(
              s"Fetching Codeforces contests: phase=$phaseOpt, includeGym=$includeGym, " +
                s"limit=${pagination.limit}, offset=${pagination.offset}"
            )
            onComplete(
              CodeforcesHandler.getContests(
                phaseOpt,
                includeGym,
                pagination.limit,
                pagination.offset,
              )
            ) {
              case Success(contests) =>
                complete(contests)
              case Failure(ex) =>
                onInternalError("Fetch Codeforces contests", ex)
            }
          }
        }
      }
    }

  private def contestByIdRoute(
    using ExecutionContext
  ): Route =
    path("contests" / IntNumber) { id =>
      get {
        debug(s"Fetching Codeforces contest by id=$id")
        onComplete(CodeforcesHandler.getContestById(id)) {
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

end CodeforcesRouter
