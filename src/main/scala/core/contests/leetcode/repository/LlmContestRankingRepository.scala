package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.LlmContestRanking
import core.contests.leetcode.model.LlmContestRanking.LlmContestRankingId
import postgres.Repository


private class LlmContestRankingTable(tag: Tag)
    extends Table[LlmContestRanking](tag, Some(LeetCodeSchema.schemaName), "llm_contest_rankings"):

  def llmId         = column[Int]("llm_id")
  def contestSlug   = column[String]("contest_slug")
  def avgScore      = column[Double]("avg_score")
  def maxScore      = column[Double]("max_score")
  def acRate        = column[Double]("ac_rate")
  def avgTriedTimes = column[Double]("avg_tried_times")
  def updatedAt     = column[Instant]("updated_at")

  def pk = primaryKey("llm_contest_rankings_pkey", (llmId, contestSlug))

  def * = (
    llmId,
    contestSlug,
    avgScore,
    maxScore,
    acRate,
    avgTriedTimes,
    updatedAt,
  ).mapTo[LlmContestRanking]

end LlmContestRankingTable


object LlmContestRankingRepository
    extends Repository[LlmContestRanking, LlmContestRankingId, LlmContestRankingTable]:

  protected val tableQuery = TableQuery[LlmContestRankingTable]

  protected def idMatcher(id: LlmContestRankingId): LlmContestRankingTable => Rep[Boolean] =
    rankingTable =>
      rankingTable.llmId === id.llmId &&
        rankingTable.contestSlug === id.contestSlug

end LlmContestRankingRepository
