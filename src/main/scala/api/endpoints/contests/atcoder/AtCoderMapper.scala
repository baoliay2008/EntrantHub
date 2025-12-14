package api.endpoints.contests.atcoder


import AtCoderDto.*
import core.contests.atcoder.model.Contest


object AtCoderMapper:

  def toContestResponse(contest: Contest): ContestResponse =
    ContestResponse(
      id = contest.id,
      name = contest.name,
      startTime = contest.startTime,
      durationSeconds = contest.durationSeconds,
      rateChange = contest.rateChange,
      contestType = contest.contestType,
      url = contest.url,
    )

end AtCoderMapper
