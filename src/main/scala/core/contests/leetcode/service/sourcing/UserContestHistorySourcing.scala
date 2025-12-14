package core.contests.leetcode.service.sourcing


import scala.concurrent.{ ExecutionContext, Future }

import upickle.default.read

import core.contests.leetcode.model.ServerRegion
import core.contests.leetcode.model.User.UserId
import core.contests.leetcode.repository.UserContestHistoryRepository.bulkSyncByUser
import core.contests.leetcode.service.sourcing.UserContestHistorySourcingMapper.toUserContestHistories
import requests.HttpRequestManager.postJsonRequest
import util.Logging


object UserContestHistorySourcing
    extends Logging:

  private given ec: ExecutionContext = ExecutionContext.global

  private def requestUserContestRankingHistory(
    serverRegion: ServerRegion,
    userSlug: String,
  ): Future[UserContestHistorySourcingDto] =
    val (url, jsonBody) = serverRegion match
      case ServerRegion.Cn =>
        val url = serverRegion.graphQLGoUrl
        val jsonBody = ujson.Obj(
          "query" ->
            """query userContestRankingHistory($userSlug: String!) {
              |    userContestRankingHistory(userSlug: $userSlug) {
              |        attended
              |        finishTimeInSeconds
              |        rating
              |        ranking
              |        contest {
              |            title
              |            startTime
              |        }
              |    }
              |}""".stripMargin,
          "variables"     -> Map("userSlug" -> userSlug),
          "operationName" -> "userContestRankingHistory",
        )
        (url, jsonBody)
      case ServerRegion.Us =>
        val url = serverRegion.graphQLUrl
        val jsonBody = ujson.Obj(
          "query" ->
            """query userContestRankingHistory($username: String!) {
              |    userContestRankingHistory(username: $username) {
              |        attended
              |        finishTimeInSeconds
              |        rating
              |        ranking
              |        contest {
              |            title
              |            startTime
              |        }
              |    }
              |}""".stripMargin,
          "variables"     -> Map("username" -> userSlug),
          "operationName" -> "userContestRankingHistory",
        )
        (url, jsonBody)

    postJsonRequest(url, jsonBody = jsonBody).map {
      read[UserContestHistorySourcingDto](_)
    }
  end requestUserContestRankingHistory

  def upsertUserContestHistory(userId: UserId): Future[Unit] =
    val serverRegion = ServerRegion.fromDataRegion(userId.dataRegion)
    val userSlug     = userId.userSlug
    for
      dto <- requestUserContestRankingHistory(serverRegion, userSlug)
      userContestHistories = toUserContestHistories(dto, serverRegion, userSlug)
      cnt <- bulkSyncByUser(userId, userContestHistories)
      _ = info(s"$userId upsert $cnt userContestHistories")
    yield ()
end UserContestHistorySourcing
