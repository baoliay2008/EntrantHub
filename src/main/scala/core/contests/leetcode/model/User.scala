package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class User(
  dataRegion: String,
  userSlug: String,
  realName: String,
  avatarUrl: String,
  attendedContestsCount: Option[Int] = None,
  rating: Option[Double] = None,
  globalRanking: Option[Int] = None,
  updatedAt: Instant = Instant.now(),
):
  // Enforce that `userSlug` is already lowercase
  require(
    userSlug == userSlug.toLowerCase,
    s"userSlug must be lowercase (got: '$userSlug')",
  )

end User


object User:
  type UserId = (dataRegion: String, userSlug: String)

  given EntityIdMapping[User, UserId] with
    extension (u: User)
      def getId: UserId = (u.dataRegion, u.userSlug)
