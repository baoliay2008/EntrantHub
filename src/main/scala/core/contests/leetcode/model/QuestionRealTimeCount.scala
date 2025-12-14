package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class QuestionRealTimeCount(
  questionId: Int,
  timepoint: Instant,
  lang: String,
  count: Int,
)


object QuestionRealTimeCount:
  type QuestionRealTimeCountId =
    (questionId: Int, timepoint: Instant, lang: String)

  given EntityIdMapping[QuestionRealTimeCount, QuestionRealTimeCountId] with
    extension (c: QuestionRealTimeCount)
      def getId: QuestionRealTimeCountId = (c.questionId, c.timepoint, c.lang)
