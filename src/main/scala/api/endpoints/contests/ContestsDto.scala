package api.endpoints.contests


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object ContestsDto:

  enum Platform derives Writer:
    case LeetCode, Codeforces, AtCoder, CodeChef

  case class UnifiedContestResponse(
    platform: Platform,
    id: String,
    name: String,
    startTime: Instant,
    durationSeconds: Int,
    url: String,
  ) derives Writer

end ContestsDto
