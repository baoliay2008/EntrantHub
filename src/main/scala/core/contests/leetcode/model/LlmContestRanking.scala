package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class LlmContestRanking(
  llmId: Int,
  contestSlug: String,
  avgScore: Double,
  maxScore: Double,
  acRate: Double,
  avgTriedTimes: Double,
  updatedAt: Instant = Instant.now(),
)


object LlmContestRanking:
  type LlmContestRankingId = (llmId: Int, contestSlug: String)

  given EntityIdMapping[LlmContestRanking, LlmContestRankingId] with
    extension (r: LlmContestRanking)
      def getId: LlmContestRankingId = (r.llmId, r.contestSlug)
