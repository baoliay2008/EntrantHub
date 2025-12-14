package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.User.UserId
import core.contests.leetcode.model.UserContestHistory
import core.contests.leetcode.model.UserContestHistory.UserContestHistoryId
import postgres.StageableRepository


private class UserContestHistoryTable(tag: Tag, tableName: String = "user_contest_histories")
    extends Table[UserContestHistory](tag, Some(LeetCodeSchema.schemaName), tableName):

  def dataRegion            = column[String]("data_region")
  def userSlug              = column[String]("user_slug")
  def titleSlug             = column[String]("title_slug")
  def finishTimeInSeconds   = column[Int]("finish_time_in_seconds")
  def ranking               = column[Int]("ranking")
  def newRating             = column[Double]("new_rating")
  def oldRating             = column[Double]("old_rating")
  def attendedContestsCount = column[Int]("attended_contests_count")
  def updatedAt             = column[Instant]("updated_at")

  def pk = primaryKey("user_contest_histories_pkey", (userSlug, dataRegion, titleSlug))

  def * = (
    dataRegion,
    userSlug,
    titleSlug,
    finishTimeInSeconds,
    ranking,
    newRating,
    oldRating,
    attendedContestsCount,
    updatedAt,
  ).mapTo[UserContestHistory]

end UserContestHistoryTable


object UserContestHistoryRepository
    extends StageableRepository[UserContestHistory, UserContestHistoryId, UserContestHistoryTable]:

  protected def customTableQuery(name: String): TableQuery[UserContestHistoryTable] =
    TableQuery[UserContestHistoryTable](tag => UserContestHistoryTable(tag, name))

  protected val tableQuery = TableQuery[UserContestHistoryTable]

  protected def idMatcher(id: UserContestHistoryId): UserContestHistoryTable => Rep[Boolean] =
    userContestHistoryTable =>
      userContestHistoryTable.dataRegion === id.dataRegion &&
        userContestHistoryTable.userSlug === id.userSlug &&
        userContestHistoryTable.titleSlug === id.titleSlug

  def findByUser(userId: UserId): Future[Seq[UserContestHistory]] =
    find(
      where = Some(t => t.dataRegion === userId.dataRegion && t.userSlug === userId.userSlug),
      orderBy = Some(_.titleSlug.desc),
    )

  def bulkSyncByUser(
    userId: UserId,
    userContestHistories: Seq[UserContestHistory],
  ): Future[Int] =
    bulkSync(
      entities = userContestHistories,
      deleteFilter = t => t.dataRegion === userId.dataRegion && t.userSlug === userId.userSlug,
      onStagingTable = q =>
        // Before upserting into the real table, we need to update the title_slug column, which is actually the title.
        // I've thought about this carefully, it's best to do it here.
        // It's not realistic to parse the title into a slug because earliest contest slugs are irregular.
        // Querying during sourcing is slow and would mess up the sourcing code.
        // Doing it here is efficient and won't cause inconsistencies because it's on the staging table.
        // Maybe a better way would be to have a single mapper in memory, but I don't want to write that code just for this one workaround.
        // THINK TWICE before refactoring this code!
        // -- Li Bao wrote a reminder for his future self
        val stagingFullName  = fullTableNameOf(q)
        val contestsFullName = ContestRepository.fullTableName
        // Update US: replace staging.title_slug (which currently holds titleUs) with the canonical title_slug
        val updateUs =
          sqlu"""
            UPDATE #$stagingFullName AS s
            SET title_slug = c.title_slug
            FROM #$contestsFullName AS c
            WHERE s.data_region = 'US' AND s.title_slug = c.title_us
          """
        // Update CN: replace staging.title_slug (which currently holds titleCn) with the canonical title_slug
        val updateCn =
          sqlu"""
            UPDATE #$stagingFullName AS s
            SET title_slug = c.title_slug
            FROM #$contestsFullName AS c
            WHERE s.data_region = 'CN' AND s.title_slug = c.title_cn
          """
        if userId.dataRegion == "CN" then updateCn
        else updateUs,
    )

end UserContestHistoryRepository
