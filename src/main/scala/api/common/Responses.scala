package api.common


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object Responses:

  case class ErrorResponse(
    message: String,
    timestamp: Instant = Instant.now(),
  ) derives Writer

  case class PaginatedResponse[T](
    items: Seq[T],
    total: Long,
    limit: Int,
    offset: Int,
  ) derives Writer

end Responses
