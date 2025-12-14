package api.endpoints.contests.codeforces


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object CodeforcesDto:

  case class ContestResponse(
    id: Int,
    name: String,
    contestType: String,
    phase: String,
    frozen: Boolean,
    durationSeconds: Int,
    startTime: Option[Instant],
    url: String,
    isGym: Boolean,
    difficulty: Option[Int],
  ) derives Writer

end CodeforcesDto
