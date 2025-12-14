package core.contests.leetcode.repository

import postgres.{ Repository, Schema }


object LeetCodeSchema extends Schema:

  val schemaName = "leetcode"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      ContestRepository,
      LlmContestRankingRepository,
      LlmRepository,
      QuestionRealTimeCountRepository,
      QuestionRepository,
      RankingRepository,
      SubmissionRepository,
      UserContestHistoryRepository,
      UserRepository,
    )

end LeetCodeSchema
