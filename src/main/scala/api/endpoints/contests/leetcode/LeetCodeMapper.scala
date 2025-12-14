package api.endpoints.contests.leetcode


import LeetCodeDto.*
import core.contests.leetcode.model.{
  Contest,
  Question,
  QuestionRealTimeCount,
  Ranking,
  User,
  UserContestHistory,
}


object LeetCodeMapper:

  def toContestResponse(contest: Contest): ContestResponse =
    ContestResponse(
      titleSlug = contest.titleSlug,
      startTime = contest.startTime,
      durationSeconds = contest.durationSeconds,
      titleUs = contest.titleUs,
      titleCn = contest.titleCn,
      contestType = contest.contestType.map(_.toString),
      contestNum = contest.contestNum,
      userNumTotal = contest.userNumTotal,
      urlUs = contest.urlUs,
      urlCn = contest.urlCn,
    )

  def toQuestionResponse(
    question: Question,
    countData: Seq[QuestionRealTimeCount],
  ): QuestionResponse =
    val counts = countData
      .sortBy(_.timepoint)
      .groupMap(_.lang)(_.count)
    QuestionResponse(
      id = question.id,
      titleSlug = question.titleSlug,
      titleUs = question.titleUs,
      titleCn = question.titleCn,
      difficulty = question.difficulty,
      credit = question.credit,
      counts = counts,
    )
  end toQuestionResponse

  def toRankingResponse(ranking: Ranking): RankingResponse =
    RankingResponse(
      dataRegion = ranking.dataRegion,
      userSlug = ranking.userSlug,
      rank = ranking.rank,
      score = ranking.score,
      finishTime = ranking.finishTime,
      oldRating = ranking.oldRating,
      newRating = ranking.newRating,
      deltaRating = ranking.deltaRating,
    )

  def toUserResponse(user: User): UserResponse =
    UserResponse(
      dataRegion = user.dataRegion,
      userSlug = user.userSlug,
      realName = user.realName,
      avatarUrl = user.avatarUrl,
      attendedContestsCount = user.attendedContestsCount,
      rating = user.rating,
      globalRanking = user.globalRanking,
    )

  def toUserContestHistoryResponse(
    history: UserContestHistory
  ): UserContestHistoryResponse =
    UserContestHistoryResponse(
      titleSlug = history.titleSlug,
      finishTimeInSeconds = history.finishTimeInSeconds,
      ranking = history.ranking,
      newRating = history.newRating,
      oldRating = history.oldRating,
      attendedContestsCount = history.attendedContestsCount,
    )

  def toRealTimeDataResponse(
    contestTitleSlug: String,
    dataRegion: String,
    userSlug: String,
    ranks: Seq[Int],
    ratings: Seq[Double],
  ): RealTimeDataResponse =
    RealTimeDataResponse(
      contestTitleSlug = contestTitleSlug,
      dataRegion = dataRegion,
      userSlug = userSlug,
      ranks = ranks,
      ratings = ratings,
    )

end LeetCodeMapper
