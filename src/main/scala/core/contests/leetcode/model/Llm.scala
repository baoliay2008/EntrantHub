package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Llm(
  id: Int,
  name: String,
  logoUrl: String,
  companyName: String,
  info: String,
  updatedAt: Instant = Instant.now(),
)


object Llm:
  type LlmId = Int

  given EntityIdMapping[Llm, LlmId] with
    extension (a: Llm)
      def getId: LlmId = a.id
