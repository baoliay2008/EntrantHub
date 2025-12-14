package api.endpoints.contests.atcoder


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object AtCoderDto:

  case class ContestResponse(
    id: String,
    name: String,
    startTime: Instant,
    durationSeconds: Int,
    rateChange: String,
    contestType: String,
    url: String,
  ) derives Writer

end AtCoderDto
