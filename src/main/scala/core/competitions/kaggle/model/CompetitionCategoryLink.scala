package core.competitions.kaggle.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class CompetitionCategoryLink(
  competitionId: Int,
  categoryName: String,
  updatedAt: Instant = Instant.now(),
)


object CompetitionCategoryLink:

  type CompetitionCategoryLinkId = (competitionId: Int, categoryName: String)

  given EntityIdMapping[CompetitionCategoryLink, CompetitionCategoryLinkId] with
    extension (l: CompetitionCategoryLink)
      def getId: CompetitionCategoryLinkId = (l.competitionId, l.categoryName)
