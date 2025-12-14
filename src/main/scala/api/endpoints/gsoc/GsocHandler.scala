package api.endpoints.gsoc


import scala.concurrent.{ ExecutionContext, Future }

import GsocDto.*
import api.common.Responses.PaginatedResponse
import core.misc.gsoc.repository.{ CategoryRepository, OrganizationRepository, ProjectRepository }
import util.Logging


object GsocHandler
    extends Logging:

  def getCategories()(
    using ExecutionContext
  ): Future[List[String]] =
    CategoryRepository.find()
      .map { categories =>
        categories.map(_.name).toList
      }

  def getOrganizations(
    year: Int,
    categoryOpt: Option[String],
    limit: Int,
    offset: Int,
  )(
    using ExecutionContext
  ): Future[PaginatedResponse[OrganizationResponse]] =
    // Single parallel repository call
    OrganizationRepository.findByYearPaginatedWithCategories(
      year,
      categoryOpt,
      limit,
      offset,
    ).map { (orgsWithCategories, total) =>
      PaginatedResponse(
        items = orgsWithCategories.map { (org, categories) =>
          GsocMapper.toOrganizationResponse(org, categories.toList)
        },
        total = total,
        limit = limit,
        offset = offset,
      )
    }

  def getProjects(
    year: Int,
    organizationSlug: String,
  )(
    using ExecutionContext
  ): Future[List[ProjectResponse]] =
    ProjectRepository.findByYearAndOrganizationWithMilestones(year, organizationSlug)
      .map { projectsWithMilestones =>
        projectsWithMilestones.map { (project, milestones) =>
          GsocMapper.toProjectResponse(project, milestones.toList)
        }.toList
      }

end GsocHandler
