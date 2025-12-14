package api.endpoints.contests.leetcode


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object LeetCodeDto:

  case class ContestResponse(
    titleSlug: String,
    startTime: Instant,
    durationSeconds: Int,
    titleUs: String,
    titleCn: String,
    contestType: Option[String],
    contestNum: Option[Int],
    userNumTotal: Option[Int],
    urlUs: String,
    urlCn: String,
  ) derives Writer

  case class QuestionResponse(
    id: Int,
    titleSlug: String,
    titleUs: String,
    titleCn: String,
    difficulty: Int,
    credit: Int,
    counts: Map[String, Seq[Int]],
  ) derives Writer

  case class RankingResponse(
    dataRegion: String,
    userSlug: String,
    rank: Int,
    score: Int,
    finishTime: Instant,
    oldRating: Option[Double],
    newRating: Option[Double],
    deltaRating: Option[Double],
  ) derives Writer

  case class UserResponse(
    dataRegion: String,
    userSlug: String,
    realName: String,
    avatarUrl: String,
    attendedContestsCount: Option[Int],
    rating: Option[Double],
    globalRanking: Option[Int],
  ) derives Writer

  case class UserContestHistoryResponse(
    titleSlug: String,
    finishTimeInSeconds: Int,
    ranking: Int,
    newRating: Double,
    oldRating: Double,
    attendedContestsCount: Int,
  ) derives Writer

  case class RealTimeDataResponse(
    contestTitleSlug: String,
    dataRegion: String,
    userSlug: String,
    ranks: Seq[Int],
    ratings: Seq[Double],
  ) derives Writer

end LeetCodeDto
