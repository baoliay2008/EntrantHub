package api.endpoints.contests


import java.time.Instant

import ContestsDto.*
import core.contests.atcoder.model.Contest as AtCoderContest
import core.contests.codechef.model.Contest as CodeChefContest
import core.contests.codeforces.model.Contest as CodeforcesContest
import core.contests.leetcode.model.Contest as LeetCodeContest


object ContestsMapper:

  def fromLeetCode(contest: LeetCodeContest): UnifiedContestResponse =
    UnifiedContestResponse(
      platform = Platform.LeetCode,
      id = contest.titleSlug,
      name = contest.titleUs,
      startTime = contest.startTime,
      durationSeconds = contest.durationSeconds,
      url = contest.urlUs,
    )

  def fromCodeforces(contest: CodeforcesContest): UnifiedContestResponse =
    val url = if contest.isGym then
      s"https://codeforces.com/gym/${contest.id}"
    else
      s"https://codeforces.com/contest/${contest.id}"

    UnifiedContestResponse(
      platform = Platform.Codeforces,
      id = contest.id.toString,
      name = contest.name,
      startTime = contest.startTime.getOrElse(Instant.EPOCH),
      durationSeconds = contest.durationSeconds,
      url = url,
    )
  end fromCodeforces

  def fromAtCoder(contest: AtCoderContest): UnifiedContestResponse =
    UnifiedContestResponse(
      platform = Platform.AtCoder,
      id = contest.id,
      name = contest.name,
      startTime = contest.startTime,
      durationSeconds = contest.durationSeconds,
      url = contest.url,
    )

  def fromCodeChef(contest: CodeChefContest): UnifiedContestResponse =
    UnifiedContestResponse(
      platform = Platform.CodeChef,
      id = contest.id,
      name = contest.name,
      startTime = contest.startTime,
      durationSeconds = contest.durationSeconds,
      url = s"https://www.codechef.com/${contest.id}",
    )

end ContestsMapper
