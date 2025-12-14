package api.endpoints.contests


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Router
import api.endpoints.contests.atcoder.AtCoderRouter
import api.endpoints.contests.codechef.CodeChefRouter
import api.endpoints.contests.codeforces.CodeforcesRouter
import api.endpoints.contests.leetcode.LeetCodeRouter


object ContestsRouter extends Router:

  private val validStatusValues = Set("all", "upcoming", "past")

  private def validateStatus(status: String): Unit =
    if !validStatusValues.contains(status) then
      throw IllegalArgumentException(
        s"Invalid status: $status. Must be one of: ${validStatusValues.mkString(", ")}"
      )

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("contests") {
      rootRoute ~
        LeetCodeRouter.routes ~
        CodeforcesRouter.routes ~
        AtCoderRouter.routes ~
        CodeChefRouter.routes
    }

  private def rootRoute(
    using ExecutionContext
  ): Route =
    pathEndOrSingleSlash {
      get {
        parameters("status".?("all")) { status =>
          validateStatus(status)

          debug(s"Fetching aggregate contests: status=$status")

          val handler = status match
            case "upcoming" => ContestsHandler.getUpcomingContests()
            case "past"     => ContestsHandler.getPastContests()
            case _          => ContestsHandler.getRecentAndUpcomingContests()

          onComplete(handler) {
            case Success(contests) =>
              complete(contests)
            case Failure(ex) =>
              onInternalError(s"Fetch aggregate contests with status=$status", ex)
          }
        }
      }
    }

end ContestsRouter
