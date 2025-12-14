package api.endpoints.contests.atcoder


import scala.concurrent.{ ExecutionContext, Future }

import AtCoderDto.*
import api.common.Responses.PaginatedResponse
import core.contests.atcoder.repository.ContestRepository
import util.Logging


object AtCoderHandler extends Logging:

  def getContests(
    contestTypeFilter: Option[String],
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[ContestResponse]] =
    ContestRepository.findPaginated(contestTypeFilter, limit, offset)
      .map { (contests, total) =>
        PaginatedResponse(
          items = contests.map(AtCoderMapper.toContestResponse),
          total = total,
          limit = limit,
          offset = offset,
        )
      }

  def getContestById(
    id: String
  )(
    using ExecutionContext
  ): Future[Option[ContestResponse]] =
    ContestRepository.findBy(id)
      .map(_.map(AtCoderMapper.toContestResponse))

end AtCoderHandler
