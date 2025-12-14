package api.endpoints.contests.codeforces


import CodeforcesDto.*
import core.contests.codeforces.model.Contest


object CodeforcesMapper:

  def toContestResponse(contest: Contest): ContestResponse =
    ContestResponse(
      id = contest.id,
      name = contest.name,
      contestType = contest.contestType,
      phase = contest.phase,
      frozen = contest.frozen,
      durationSeconds = contest.durationSeconds,
      startTime = contest.startTime,
      url = s"https://codeforces.com/contest/${contest.id}",
      isGym = contest.isGym,
      difficulty = contest.difficulty,
    )

end CodeforcesMapper
