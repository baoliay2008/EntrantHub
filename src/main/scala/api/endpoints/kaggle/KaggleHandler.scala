package api.endpoints.kaggle


import scala.concurrent.{ ExecutionContext, Future }

import KaggleDto.*
import api.common.Responses.PaginatedResponse
import core.competitions.kaggle.repository.CompetitionRepository
import util.Logging


object KaggleHandler
    extends Logging:

  def getCompetitions(
    sortBy: String,
    sortOrder: String,
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[CompetitionResponse]] =
    CompetitionRepository.findRecentPaginated(sortBy, sortOrder, limit, offset)
      .map { (data, total) =>
        PaginatedResponse(
          items = data.map { (competition, organization, categories) =>
            KaggleMapper.toCompetitionResponse(competition, organization, categories.toList)
          },
          total = total,
          limit = limit,
          offset = offset,
        )
      }

  def getCompetitionById(id: Int)(
    using ExecutionContext
  ): Future[Option[CompetitionResponse]] =
    CompetitionRepository.findById(id)
      .map(_.map { (competition, organization, categories) =>
        KaggleMapper.toCompetitionResponse(competition, organization, categories.toList)
      })

end KaggleHandler
