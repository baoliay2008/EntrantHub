package core.contests.codeforces.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.codeforces.model.Contest
import core.contests.codeforces.model.Contest.ContestId
import postgres.Repository


private class ContestTable(tag: Tag)
    extends Table[Contest](tag, Some(CodeforcesSchema.schemaName), "contests"):

  def id                    = column[Int]("id", O.PrimaryKey)
  def name                  = column[String]("name")
  def contestType           = column[String]("contest_type")
  def phase                 = column[String]("phase")
  def frozen                = column[Boolean]("frozen")
  def durationSeconds       = column[Int]("duration_seconds")
  def startTime             = column[Option[Instant]]("start_time")
  def freezeDurationSeconds = column[Option[Int]]("freeze_duration_seconds")
  def preparedBy            = column[Option[String]]("prepared_by")
  def websiteUrl            = column[Option[String]]("website_url")
  def description           = column[Option[String]]("description")
  def difficulty            = column[Option[Int]]("difficulty")
  def kind                  = column[Option[String]]("kind")
  def icpcRegion            = column[Option[String]]("icpc_region")
  def country               = column[Option[String]]("country")
  def city                  = column[Option[String]]("city")
  def season                = column[Option[String]]("season")
  def updatedAt             = column[Instant]("updated_at")

  def idxStartTime = index("contests_start_time_idx", startTime)

  def * = (
    id,
    name,
    contestType,
    phase,
    frozen,
    durationSeconds,
    startTime,
    freezeDurationSeconds,
    preparedBy,
    websiteUrl,
    description,
    difficulty,
    kind,
    icpcRegion,
    country,
    city,
    season,
    updatedAt,
  ).mapTo[Contest]

end ContestTable


object ContestRepository extends Repository[Contest, ContestId, ContestTable]:

  protected val tableQuery = TableQuery[ContestTable]

  protected def idMatcher(id: ContestId): ContestTable => Rep[Boolean] =
    _.id === id

  def findPaginated(
    phaseFilter: Option[String],
    includeGym: Boolean,
    limit: Int,
    offset: Int,
  ): Future[(Seq[Contest], Int)] =
    import postgres.SlickExtensions.paginate

    // 1. Base Query
    // First apply the optional phase filter
    // Then apply the Gym logic
    // If includeGym is true, we keep everything.
    // If includeGym is false, we add a filter to exclude Gym IDs.
    val baseQuery = tableQuery
      .filterOpt(phaseFilter)(_.phase === _)
      .filterIf(!includeGym)(_.id < 100000) // Codeforces convention: Gym IDs are >= 100000

    // 2. Create Actions
    val countAction = baseQuery
      .length
      .result
    val fetchAction = baseQuery
      .sortBy(_.startTime.desc.nullsLast) // Keep nullsLast as per original code
      .paginate(Some(limit), Some(offset))
      .result

    // 3. Run in Parallel
    db.run(fetchAction.zip(countAction))
  end findPaginated

end ContestRepository
