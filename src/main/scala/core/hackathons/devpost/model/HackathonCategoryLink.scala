package core.hackathons.devpost.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class HackathonCategoryLink(
  hackathonId: Int,
  categoryId: Int,
  updatedAt: Instant = Instant.now(),
)


object HackathonCategoryLink:
  type HackathonCategoryLinkId = (hackathonId: Int, categoryId: Int)

  given EntityIdMapping[HackathonCategoryLink, HackathonCategoryLinkId] with
    extension (l: HackathonCategoryLink)
      def getId: HackathonCategoryLinkId = (l.hackathonId, l.categoryId)
