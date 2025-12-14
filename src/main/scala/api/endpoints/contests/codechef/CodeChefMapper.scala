package api.endpoints.contests.codechef


import CodeChefDto.*
import core.contests.codechef.model.Contest


object CodeChefMapper:

  def toContestResponse(contest: Contest): ContestResponse =
    ContestResponse(
      id = contest.id,
      name = contest.name,
      startTime = contest.startTime,
      endTime = contest.endTime,
      durationSeconds = contest.durationSeconds,
      distinctUsers = contest.distinctUsers,
      url = s"https://www.codechef.com/${contest.id}",
    )

end CodeChefMapper
