package api.endpoints.devpost


import scala.concurrent.{ ExecutionContext, Future }

import DevPostDto.*
import api.common.Responses.PaginatedResponse
import core.hackathons.devpost.repository.{
  CategoryRepository,
  HackathonCategoryLinkRepository,
  HackathonRepository,
}
import util.Logging


object DevPostHandler
    extends Logging:

  def getCategories()(
    using ExecutionContext
  ): Future[List[CategoryResponse]] =
    CategoryRepository.find()
      .map(DevPostMapper.toCategoryResponseList)

  def getHackathons(
    categoryIdOpt: Option[Int],
    openStateOpt: Option[String],
    sortBy: String,
    sortOrder: String,
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[HackathonResponse]] =

    // Single call to repo (Parallel execution internally via zip)
    HackathonRepository.findPaginatedWithCategories(
      categoryIdOpt,
      openStateOpt,
      sortBy,
      sortOrder,
      limit,
      offset,
    ).map { (hackathons, total) =>
      PaginatedResponse(
        items = hackathons.map { (hackathon, categories) =>
          DevPostMapper.toHackathonResponse(hackathon, categories.toList)
        },
        total = total,
        limit = limit,
        offset = offset,
      )
    }

  def getHackathonById(id: Int)(
    using ExecutionContext
  ): Future[Option[HackathonResponse]] =
    HackathonRepository.findByIdWithCategories(id)
      .map(_.map { (hackathon, categories) =>
        DevPostMapper.toHackathonResponse(hackathon, categories.toList)
      })

end DevPostHandler
