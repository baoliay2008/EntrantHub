package core.competitions.kaggle.repository

import postgres.{ Repository, Schema }


object KaggleSchema extends Schema:

  val schemaName = "kaggle"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      CompetitionRepository,
      CategoryRepository,
      OrganizationRepository,
      CompetitionCategoryLinkRepository,
    )

end KaggleSchema
