package core.misc.gsoc.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.misc.gsoc.model.Project.ProjectId
import core.misc.gsoc.model.{ Milestone, Project }
import postgres.{ Repository, SlickJsonStringColumnSupport }


private class ProjectTable(tag: Tag)
    extends Table[Project](tag, Some(GsocSchema.schemaName), "projects"):

  import SlickJsonStringColumnSupport.stringListColumnType

  def id               = column[String]("id", O.PrimaryKey)
  def year             = column[Int]("year")
  def title            = column[String]("title")
  def programSlug      = column[String]("program_slug")
  def organizationName = column[String]("organization_name")
  def organizationSlug = column[String]("organization_slug")
  def contributorName  = column[String]("contributor_name")
  def contributorId    = column[Option[String]]("contributor_id")
  def mentors          = column[List[String]]("mentors")
  def description      = column[String]("description")
  def shortDescription = column[String]("short_description")
  def techTags         = column[List[String]]("tech_tags")
  def topicTags        = column[List[String]]("topic_tags")
  def size             = column[Option[String]]("size")
  def status           = column[Option[String]]("status")
  def phase            = column[Option[String]]("phase")
  def proposalId       = column[Option[String]]("proposal_id")
  def projectCodeUrl   = column[Option[String]]("project_code_url")
  def dateCreated      = column[Instant]("date_created")
  def dateUpdated      = column[Option[Instant]]("date_updated")
  def dateUploaded     = column[Option[Instant]]("date_uploaded")
  def updatedAt        = column[Instant]("updated_at")

  def idxYear    = index("projects_year_idx", year)
  def idxOrgName = index("projects_org_name_idx", organizationName)
  def idxOrgSlug = index("projects_org_slug_idx", organizationSlug)

  def * = (
    id,
    year,
    title,
    programSlug,
    organizationName,
    organizationSlug,
    contributorName,
    contributorId,
    mentors,
    description,
    shortDescription,
    techTags,
    topicTags,
    size,
    status,
    phase,
    proposalId,
    projectCodeUrl,
    dateCreated,
    dateUpdated,
    dateUploaded,
    updatedAt,
  ).mapTo[Project]

end ProjectTable


object ProjectRepository extends Repository[Project, ProjectId, ProjectTable]:

  protected val tableQuery = TableQuery[ProjectTable]

  protected def idMatcher(id: ProjectId): ProjectTable => Rep[Boolean] =
    _.id === id

  /** Find projects by year and organization with their milestones
    */
  def findByYearAndOrganizationWithMilestones(
    year: Int,
    organizationSlug: String,
  ): Future[Seq[(Project, Seq[Milestone])]] =
    val milestoneTableQuery = MilestoneRepository.tableQuery

    val query =
      tableQuery
        .filter(p => p.year === year && p.organizationSlug === organizationSlug)
        .joinLeft(milestoneTableQuery)
        .on(_.id === _.projectId)

    db.run(query.result).map { results =>
      results
        .groupMap(_._1)(_._2)
        .view
        .mapValues(_.flatten) // Convert Seq[Option[Milestone]] to Seq[Milestone]
        .toSeq
        .sortBy(_._1.dateCreated)
    }
  end findByYearAndOrganizationWithMilestones

end ProjectRepository
