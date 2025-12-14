package core.misc.gsoc.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.misc.gsoc.model.Milestone
import core.misc.gsoc.model.Milestone.MilestoneId
import postgres.Repository


private class MilestoneTable(tag: Tag)
    extends Table[Milestone](tag, Some(GsocSchema.schemaName), "milestones"):

  def projectId = column[String]("project_id")
  def name      = column[String]("name")
  def datetime  = column[Instant]("datetime")
  def phase     = column[String]("phase")
  def updatedAt = column[Instant]("updated_at")

  def pk = primaryKey("milestones_pkey", (projectId, name))

  def * = (
    projectId,
    name,
    datetime,
    phase,
    updatedAt,
  ).mapTo[Milestone]

end MilestoneTable


object MilestoneRepository extends Repository[Milestone, MilestoneId, MilestoneTable]:

  val tableQuery = TableQuery[MilestoneTable]

  protected def idMatcher(id: MilestoneId): MilestoneTable => Rep[Boolean] =
    t => t.projectId === id.projectId && t.name === id.name

end MilestoneRepository
