package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class UserContestHistory(
  dataRegion: String,
  userSlug: String,
  titleSlug: String,
  finishTimeInSeconds: Int,
  ranking: Int,
  newRating: Double,
  oldRating: Double,
  attendedContestsCount: Int,
  updatedAt: Instant = Instant.now(),
):
  // Enforce that `userSlug` is already lowercase
  require(
    userSlug == userSlug.toLowerCase,
    s"userSlug must be lowercase (got: '$userSlug')",
  )

end UserContestHistory


object UserContestHistory:
  type UserContestHistoryId = (dataRegion: String, userSlug: String, titleSlug: String)

  given EntityIdMapping[UserContestHistory, UserContestHistoryId] with
    extension (u: UserContestHistory)
      def getId: UserContestHistoryId = (u.dataRegion, u.userSlug, u.titleSlug)
