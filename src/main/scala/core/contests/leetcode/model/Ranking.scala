package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Ranking(
  contestTitleSlug: String,
  dataRegion: String,
  userSlug: String,
  rank: Int,
  score: Int,
  finishTime: Instant,
  attendedContestsCount: Option[Int] = None,
  oldRating: Option[Double] = None,
  expectedRating: Option[Double] = None,
  deltaRating: Option[Double] = None,
  updatedAt: Instant = Instant.now(),
):
  // Enforce that `userSlug` is already lowercase
  require(
    userSlug == userSlug.toLowerCase,
    s"userSlug must be lowercase (got: '$userSlug')",
  )

  def newRating: Option[Double] = oldRating.flatMap(old => deltaRating.map(old + _))

end Ranking


object Ranking:
  type RankingId = (contestTitleSlug: String, dataRegion: String, userSlug: String)

  given EntityIdMapping[Ranking, RankingId] with
    extension (r: Ranking)
      def getId: RankingId =
        (r.contestTitleSlug, r.dataRegion, r.userSlug)

  // initialize `attendedContestsCount` and `rating` with default values for NEW users.
  val DefaultAttendedContestsCount: Int = 0
  val DefaultRating: Double             = 1500.0

end Ranking
