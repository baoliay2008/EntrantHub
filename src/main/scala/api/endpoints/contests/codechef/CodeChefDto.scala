package api.endpoints.contests.codechef


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object CodeChefDto:

  case class ContestResponse(
    id: String,
    name: String,
    startTime: Instant,
    endTime: Instant,
    durationSeconds: Int,
    distinctUsers: Int,
    url: String,
  ) derives Writer

end CodeChefDto
