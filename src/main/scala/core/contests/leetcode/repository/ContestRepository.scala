package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.Contest
import core.contests.leetcode.model.Contest.ContestId
import postgres.Repository
import util.FutureUtils.runInBatchesAndCollect


private class ContestTable(tag: Tag)
    extends Table[Contest](tag, Some(LeetCodeSchema.schemaName), "contests"):

  def titleSlug         = column[String]("title_slug", O.PrimaryKey)
  def startTime         = column[Instant]("start_time")
  def durationSeconds   = column[Int]("duration_seconds")
  def titleUs           = column[String]("title_us")
  def titleCn           = column[String]("title_cn")
  def unratedUs         = column[Boolean]("unrated_us")
  def unratedCn         = column[Boolean]("unrated_cn")
  def rankingUpdatedUs  = column[Boolean]("ranking_updated_us")
  def rankingUpdatedCn  = column[Boolean]("ranking_updated_cn")
  def registerUserNumUs = column[Int]("register_user_num_us")
  def registerUserNumCn = column[Int]("register_user_num_cn")
  def userNumUs         = column[Option[Int]]("user_num_us")
  def userNumCn         = column[Option[Int]]("user_num_cn")
  def discussUrlUs      = column[Option[String]]("discuss_url_us")
  def discussUrlCn      = column[Option[String]]("discuss_url_cn")
  def updatedAt         = column[Instant]("updated_at")
  def predictedAt       = column[Option[Instant]]("predicted_at")

  def idxStartTime = index("contests_start_time_idx", startTime)

  def * = (
    titleSlug,
    startTime,
    durationSeconds,
    titleUs,
    titleCn,
    unratedUs,
    unratedCn,
    rankingUpdatedUs,
    rankingUpdatedCn,
    registerUserNumUs,
    registerUserNumCn,
    userNumUs,
    userNumCn,
    discussUrlUs,
    discussUrlCn,
    updatedAt,
    predictedAt,
  ).mapTo[Contest]

end ContestTable


object ContestRepository extends Repository[Contest, ContestId, ContestTable]:

  protected val tableQuery = TableQuery[ContestTable]

  protected def idMatcher(id: ContestId): ContestTable => Rep[Boolean] =
    _.titleSlug === id

  def upsertFromSourcing(sourcedContest: Contest): Future[Contest] =
    for
      existingOpt <- findBy(sourcedContest.titleSlug)
      contest <- existingOpt match
        case Some(existing) =>
          val updated = sourcedContest.copy(predictedAt = existing.predictedAt)
          upsertOne(updated).map(_ => updated)
        case None =>
          insertOne(sourcedContest).map(_ => sourcedContest)
    yield contest

  def upsertFromSourcing(sourcedContests: Seq[Contest]): Future[Seq[Contest]] =
    runInBatchesAndCollect(sourcedContests)(upsertFromSourcing)

  def recentPast(n: Int): Future[Seq[Contest]] =
    val now = Instant.now()
    find(
      where = Some(_.startTime <= now),
      orderBy = Some(_.startTime.desc),
      limit = Some(n),
    )

  def upcoming(n: Int): Future[Seq[Contest]] =
    val now = Instant.now()
    find(
      where = Some(_.startTime > now),
      orderBy = Some(_.startTime.asc),
      limit = Some(n),
    )

  def findAllPastTitleSlugs(): Future[Seq[ContestId]] =
    val now = Instant.now()
    findColumns(
      select = _.titleSlug,
      where = Some(_.startTime <= now),
      orderBy = Some(_.startTime.desc),
    )

  def findTitleSlugsSince(contestId: ContestId): Future[Seq[ContestId]] =
    for
      contestOpt <- findBy(contestId)
      titleSlugs <- contestOpt match
        case Some(contest) => findColumns(
            select = _.titleSlug,
            where = Some(_.startTime >= contest.startTime),
            orderBy = Some(_.startTime.asc),
          )
        case None => Future.successful(Seq.empty)
    yield titleSlugs

  def findPaginated(
    status: Option[String], // "upcoming", "past", or None for all
    limit: Int,
    offset: Int,
  ): Future[(Seq[Contest], Int)] =
    import postgres.SlickExtensions.paginate
    val now = Instant.now()

    // 1. Build Base Query
    val baseQuery = status match
      case Some("upcoming") =>
        tableQuery.filter(_.startTime > now)
      case Some("past") =>
        tableQuery.filter(_.startTime <= now)
      case _ =>
        tableQuery

    // 2. Define Sort Order
    val sortedQuery = status match
      case Some("upcoming") => baseQuery.sortBy(_.startTime.asc)
      case _                => baseQuery.sortBy(_.startTime.desc)

    // 3. Create Actions
    val countAction = baseQuery
      .length
      .result
    val fetchAction = sortedQuery
      .paginate(Some(limit), Some(offset))
      .result

    // 4. Run in Parallel
    db.run(fetchAction.zip(countAction))
  end findPaginated

end ContestRepository
