package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.User
import core.contests.leetcode.model.User.UserId
import postgres.Repository


private class UserTable(tag: Tag)
    extends Table[User](tag, Some(LeetCodeSchema.schemaName), "users"):

  def dataRegion            = column[String]("data_region")
  def userSlug              = column[String]("user_slug")
  def realName              = column[String]("real_name")
  def avatarUrl             = column[String]("avatar_url")
  def attendedContestsCount = column[Option[Int]]("attended_contests_count")
  def rating                = column[Option[Double]]("rating")
  def globalRanking         = column[Option[Int]]("global_ranking")
  def updatedAt             = column[Instant]("updated_at")

  def pk = primaryKey("users_pkey", (userSlug, dataRegion))

  def * = (
    dataRegion,
    userSlug,
    realName,
    avatarUrl,
    attendedContestsCount,
    rating,
    globalRanking,
    updatedAt,
  ).mapTo[User]

end UserTable


object UserRepository extends Repository[User, UserId, UserTable]:

  protected val tableQuery = TableQuery[UserTable]

  protected def idMatcher(id: UserId): UserTable => Rep[Boolean] =
    userTable =>
      userTable.dataRegion === id.dataRegion &&
        userTable.userSlug === id.userSlug

  def isRecentlyUpdated(
    userId: UserId,
    since: Instant,
  ): Future[Boolean] =
    exists { userTable =>
      userTable.dataRegion === userId.dataRegion &&
      userTable.userSlug === userId.userSlug &&
      userTable.updatedAt >= since
    }

end UserRepository
