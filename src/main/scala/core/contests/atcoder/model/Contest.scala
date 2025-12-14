package core.contests.atcoder.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Contest(
  id: String, // e.g., "abc200", "arc100"
  name: String,
  startTime: Instant,
  durationSeconds: Int,
  rateChange: String,         // "~ 1199", "~ 1999", "All"
  contestType: String,        // "Algorithm", "Heuristic"
  ratedColor: Option[String], // "user-blue", "user-orange", etc.
  updatedAt: Instant = Instant.now(),
):
  def url: String = s"https://atcoder.jp/contests/$id"

end Contest


object Contest:
  type ContestId = String

  given EntityIdMapping[Contest, ContestId] with
    extension (c: Contest)
      def getId: ContestId = c.id
