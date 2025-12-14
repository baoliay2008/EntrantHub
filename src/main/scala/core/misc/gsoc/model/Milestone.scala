package core.misc.gsoc.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Milestone(
  projectId: String,
  name: String,
  datetime: Instant,
  phase: String,
  updatedAt: Instant = Instant.now(),
)


object Milestone:
  type MilestoneId = (projectId: String, name: String)

  given EntityIdMapping[Milestone, MilestoneId] with
    extension (m: Milestone)
      def getId: MilestoneId = (m.projectId, m.name)
