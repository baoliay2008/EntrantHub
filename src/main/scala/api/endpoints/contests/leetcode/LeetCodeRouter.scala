package api.endpoints.contests.leetcode


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.Responses.ErrorResponse
import api.common.{ CustomDirectives, Router }


object LeetCodeRouter extends Router:

  private val validStatusValues = Set("upcoming", "past", "all")
  private val validDataRegions  = Set("US", "CN")

  private def validateStatus(status: String): Unit =
    if !validStatusValues.contains(status) then
      throw IllegalArgumentException(
        s"Invalid status: $status. Must be one of: ${validStatusValues.mkString(", ")}"
      )

  private def validateDataRegion(dataRegion: String): Unit =
    if !validDataRegions.contains(dataRegion.toUpperCase) then
      throw IllegalArgumentException(
        s"Invalid dataRegion: $dataRegion. Must be one of: ${validDataRegions.mkString(", ")}"
      )

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("leetcode") {
      contestsRoute ~
        contestBySlugRoute ~
        contestQuestionsRoute ~
        contestRankingsRoute ~
        userRealTimeDataRoute ~
        userProfileRoute ~
        userContestHistoryRoute
    }

  private def contestsRoute(
    using ExecutionContext
  ): Route =
    path("contests") {
      get {
        parameters("status".optional) { statusOpt =>
          statusOpt.foreach(validateStatus)

          CustomDirectives.withPagination() { pagination =>
            debug(
              s"Fetching LeetCode contests: status=$statusOpt, " +
                s"limit=${pagination.limit}, offset=${pagination.offset}"
            )
            onComplete(
              LeetCodeHandler.getContests(
                statusOpt,
                pagination.limit,
                pagination.offset,
              )
            ) {
              case Success(contests) =>
                complete(contests)
              case Failure(ex) =>
                onInternalError(s"Fetch LeetCode contests", ex)
            }
          }
        }
      }
    }

  private def contestBySlugRoute(
    using ExecutionContext
  ): Route =
    path("contests" / Segment) { titleSlug =>
      get {
        debug(s"Fetching LeetCode contest by titleSlug=$titleSlug")
        onComplete(LeetCodeHandler.getContestBySlug(titleSlug)) {
          case Success(Some(contest)) =>
            complete(contest)
          case Success(None) =>
            complete(
              StatusCodes.NotFound,
              ErrorResponse(s"Contest with titleSlug=$titleSlug not found"),
            )
          case Failure(ex) =>
            onInternalError(s"Fetch contest by titleSlug=$titleSlug", ex)
        }
      }
    }

  private def contestQuestionsRoute(
    using ExecutionContext
  ): Route =
    path("contests" / Segment / "questions") { titleSlug =>
      get {
        debug(s"Fetching questions for contest titleSlug=$titleSlug")
        onComplete(LeetCodeHandler.getContestQuestions(titleSlug)) {
          case Success(questions) =>
            complete(questions)
          case Failure(ex) =>
            onInternalError(s"Fetch questions for contest titleSlug=$titleSlug", ex)
        }
      }
    }

  private def contestRankingsRoute(
    using ExecutionContext
  ): Route =
    path("contests" / Segment / "rankings") { titleSlug =>
      get {
        CustomDirectives.withPagination() { pagination =>
          debug(
            s"Fetching rankings for contest titleSlug=$titleSlug, " +
              s"limit=${pagination.limit}, offset=${pagination.offset}"
          )
          onComplete(
            LeetCodeHandler.getContestRankings(
              titleSlug,
              pagination.limit,
              pagination.offset,
            )
          ) {
            case Success(rankings) =>
              complete(rankings)
            case Failure(ex) =>
              onInternalError(s"Fetch rankings for contest titleSlug=$titleSlug", ex)
          }
        }
      }
    }

  private def userProfileRoute(
    using ExecutionContext
  ): Route =
    path("users" / Segment / Segment) { (dataRegion, userSlug) =>
      get {
        validateDataRegion(dataRegion)
        debug(s"Fetching user profile: dataRegion=$dataRegion, userSlug=$userSlug")
        onComplete(LeetCodeHandler.getUserProfile(dataRegion.toUpperCase, userSlug)) {
          case Success(Some(user)) =>
            complete(user)
          case Success(None) =>
            complete(
              StatusCodes.NotFound,
              ErrorResponse(s"User not found: $dataRegion/$userSlug"),
            )
          case Failure(ex) =>
            onInternalError(s"Fetch user profile: $dataRegion/$userSlug", ex)
        }
      }
    }

  private def userContestHistoryRoute(
    using ExecutionContext
  ): Route =
    path("users" / Segment / Segment / "history") { (dataRegion, userSlug) =>
      get {
        validateDataRegion(dataRegion)
        debug(s"Fetching contest history: dataRegion=$dataRegion, userSlug=$userSlug")
        onComplete(LeetCodeHandler.getUserContestHistory(dataRegion.toUpperCase, userSlug)) {
          case Success(history) =>
            complete(history)
          case Failure(ex) =>
            onInternalError(s"Fetch contest history: $dataRegion/$userSlug", ex)
        }
      }
    }

  private def userRealTimeDataRoute(
    using ExecutionContext
  ): Route =
    path("contests" / Segment / "users" / Segment / Segment / "realtime") {
      (titleSlug, dataRegion, userSlug) =>
        get {
          validateDataRegion(dataRegion)
          debug(
            s"Fetching real-time data: contest=$titleSlug, " +
              s"dataRegion=$dataRegion, userSlug=$userSlug"
          )
          onComplete(
            LeetCodeHandler.getUserRealTimeData(
              titleSlug,
              dataRegion.toUpperCase,
              userSlug,
            )
          ) {
            case Success(Some(data)) =>
              complete(data)
            case Success(None) =>
              complete(
                StatusCodes.NotFound,
                ErrorResponse(
                  s"Real-time data not found for user $dataRegion/$userSlug in contest $titleSlug"
                ),
              )
            case Failure(ex) =>
              onInternalError(
                s"Fetch real-time data: contest=$titleSlug, user=$dataRegion/$userSlug",
                ex,
              )
          }
        }
    }

end LeetCodeRouter
