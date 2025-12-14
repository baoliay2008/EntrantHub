package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Submission(
  id: Long,
  questionId: Int,
  dataRegion: String,
  userSlug: String,
  timepoint: Instant,
  failCount: Int,
  lang: String,
  updatedAt: Instant = Instant.now(),
):
  // Enforce that `userSlug` is already lowercase
  require(
    userSlug == userSlug.toLowerCase,
    s"userSlug must be lowercase (got: '$userSlug')",
  )

end Submission


object Submission:
  type SubmissionId = Long

  given EntityIdMapping[Submission, SubmissionId] with
    extension (s: Submission)
      def getId: SubmissionId = s.id
