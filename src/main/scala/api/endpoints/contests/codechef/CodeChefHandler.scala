package api.endpoints.contests.codechef


import scala.concurrent.{ ExecutionContext, Future }

import CodeChefDto.*
import api.common.Responses.PaginatedResponse
import core.contests.codechef.repository.ContestRepository
import util.Logging


object CodeChefHandler extends Logging:

  def getContests(
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[ContestResponse]] =
    ContestRepository.findPaginated(limit, offset)
      .map { (contests, total) =>
        PaginatedResponse(
          items = contests.map(CodeChefMapper.toContestResponse),
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
      .map(_.map(CodeChefMapper.toContestResponse))

end CodeChefHandler
