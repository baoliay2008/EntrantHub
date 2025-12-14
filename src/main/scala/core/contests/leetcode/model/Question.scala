package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Question(
  contestTitleSlug: String,
  id: Int,
  idUs: Int,
  idCn: Int,
  titleSlug: String,
  titleUs: String,
  titleCn: String,
  difficulty: Int,
  credit: Int,
  updatedAt: Instant = Instant.now(),
)


object Question:
  type QuestionId = Int

  given EntityIdMapping[Question, QuestionId] with
    extension (q: Question)
      def getId: QuestionId = q.id
