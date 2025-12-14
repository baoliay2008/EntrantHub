package core.contests.atcoder.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.atcoder.model.Contest
import core.contests.atcoder.model.Contest.ContestId
import postgres.Repository


private class ContestTable(tag: Tag)
    extends Table[Contest](tag, Some(AtCoderSchema.schemaName), "contests"):

  def id              = column[String]("id", O.PrimaryKey)
  def name            = column[String]("name")
  def startTime       = column[Instant]("start_time")
  def durationSeconds = column[Int]("duration_seconds")
  def rateChange      = column[String]("rate_change")
  def contestType     = column[String]("contest_type")
  def ratedColor      = column[Option[String]]("rated_color")
  def updatedAt       = column[Instant]("updated_at")

  def idxStartTime = index("contests_start_time_idx", startTime)

  def * = (
    id,
    name,
    startTime,
    durationSeconds,
    rateChange,
    contestType,
    ratedColor,
    updatedAt,
  ).mapTo[Contest]

end ContestTable


object ContestRepository extends Repository[Contest, ContestId, ContestTable]:

  protected val tableQuery = TableQuery[ContestTable]

  protected def idMatcher(id: ContestId): ContestTable => Rep[Boolean] =
    _.id === id

  def findPaginated(
    contestTypeFilter: Option[String],
    limit: Int,
    offset: Int,
  ): Future[(Seq[Contest], Int)] =
    import postgres.SlickExtensions.paginate

    // 1. Base Query
    val baseQuery = tableQuery
      .filterOpt(contestTypeFilter)(_.contestType === _)

    // 2. Create Actions
    val countAction = baseQuery
      .length
      .result
    val fetchAction = baseQuery
      .sortBy(_.startTime.desc)
      .paginate(Some(limit), Some(offset))
      .result

    // 3. Run in Parallel
    db.run(fetchAction.zip(countAction))
  end findPaginated

end ContestRepository
