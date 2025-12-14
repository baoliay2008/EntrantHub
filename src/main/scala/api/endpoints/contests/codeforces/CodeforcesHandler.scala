package api.endpoints.contests.codeforces


import scala.concurrent.{ ExecutionContext, Future }

import CodeforcesDto.*
import api.common.Responses.PaginatedResponse
import core.contests.codeforces.repository.ContestRepository
import util.Logging


object CodeforcesHandler extends Logging:

  def getContests(
    phaseFilter: Option[String],
    includeGym: Boolean,
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[ContestResponse]] =
    ContestRepository.findPaginated(phaseFilter, includeGym, limit, offset)
      .map { (contests, total) =>
        PaginatedResponse(
          items = contests.map(CodeforcesMapper.toContestResponse),
          total = total,
          limit = limit,
          offset = offset,
        )
      }

  def getContestById(
    id: Int
  )(
    using ExecutionContext
  ): Future[Option[ContestResponse]] =
    ContestRepository.findBy(id)
      .map(_.map(CodeforcesMapper.toContestResponse))

end CodeforcesHandler
