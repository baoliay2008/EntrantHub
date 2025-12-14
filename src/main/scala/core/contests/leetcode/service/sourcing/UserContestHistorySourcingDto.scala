package core.contests.leetcode.service.sourcing

import upickle.default.Reader


private case class UserContestHistorySourcingDto(
  data: userContestRankingHistoryDto
) derives Reader


private case class userContestRankingHistoryDto(
  userContestRankingHistory: Option[List[UserContestHistoryItemDto]]
) derives Reader


private case class UserContestHistoryItemDto(
  attended: Boolean,
  finishTimeInSeconds: Long,
  rating: Double,
  ranking: Int,
  contest: ContestInfoDto,
) derives Reader


private case class ContestInfoDto(
  title: String,
  startTime: Long,
) derives Reader
