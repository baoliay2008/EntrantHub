package api.endpoints.contests.leetcode


import scala.concurrent.{ ExecutionContext, Future }

import LeetCodeDto.*
import api.common.Responses.PaginatedResponse
import core.contests.leetcode.model.User.UserId
import core.contests.leetcode.repository.{
  ContestRepository,
  QuestionRealTimeCountRepository,
  QuestionRepository,
  RankingRepository,
  UserContestHistoryRepository,
  UserRepository,
}
import util.Logging


object LeetCodeHandler extends Logging:

  def getContests(
    status: Option[String],
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[ContestResponse]] =
    ContestRepository.findPaginated(status, limit, offset)
      .map { (contests, total) =>
        PaginatedResponse(
          items = contests.map(LeetCodeMapper.toContestResponse),
          total = total,
          limit = limit,
          offset = offset,
        )
      }
  end getContests

  def getContestBySlug(
    titleSlug: String
  )(
    using ExecutionContext
  ): Future[Option[ContestResponse]] =
    ContestRepository.findBy(titleSlug)
      .map(_.map(LeetCodeMapper.toContestResponse))

  def getContestQuestions(
    titleSlug: String
  )(
    using ExecutionContext
  ): Future[List[QuestionResponse]] =
    for
      questions <- QuestionRepository.findByContest(titleSlug)
      countData <- QuestionRealTimeCountRepository.getCountsByContest(titleSlug)
    yield
      val countsByQuestionId = countData.groupBy(_.questionId)
      questions.map { q =>
        LeetCodeMapper.toQuestionResponse(q, countsByQuestionId.getOrElse(q.id, Seq.empty))
      }.toList

  def getContestRankings(
    titleSlug: String,
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[RankingResponse]] =
    RankingRepository.findByContestPaginated(titleSlug, limit, offset)
      .map { (rankings, total) =>
        PaginatedResponse(
          items = rankings.map(LeetCodeMapper.toRankingResponse),
          total = total,
          limit = limit,
          offset = offset,
        )
      }

  def getUserProfile(
    dataRegion: String,
    userSlug: String,
  )(
    using ExecutionContext
  ): Future[Option[UserResponse]] =
    val userId: UserId = (dataRegion, userSlug.toLowerCase)
    UserRepository.findBy(userId)
      .map(_.map(LeetCodeMapper.toUserResponse))

  def getUserContestHistory(
    dataRegion: String,
    userSlug: String,
  )(
    using ExecutionContext
  ): Future[List[UserContestHistoryResponse]] =
    val userId: UserId = (dataRegion, userSlug.toLowerCase)
    UserContestHistoryRepository.findByUser(userId)
      .map(_.map(LeetCodeMapper.toUserContestHistoryResponse).toList)

  def getUserRealTimeData(
    contestTitleSlug: String,
    dataRegion: String,
    userSlug: String,
  )(
    using ExecutionContext
  ): Future[Option[RealTimeDataResponse]] =
    val normalizedUserSlug = userSlug.toLowerCase
    for
      ranksOpt <- RankingRepository.getRealTimeRanks(
        contestTitleSlug,
        dataRegion,
        normalizedUserSlug,
      )
      ratingsOpt <-
        RankingRepository.getRealTimeRatings(
          contestTitleSlug,
          dataRegion,
          normalizedUserSlug,
        )
    yield for
      ranks   <- ranksOpt
      ratings <- ratingsOpt
    yield LeetCodeMapper.toRealTimeDataResponse(
      contestTitleSlug,
      dataRegion,
      normalizedUserSlug,
      ranks,
      ratings,
    )
    end for
  end getUserRealTimeData

end LeetCodeHandler
