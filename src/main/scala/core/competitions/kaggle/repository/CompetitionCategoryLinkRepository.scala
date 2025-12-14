package core.competitions.kaggle.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.competitions.kaggle.model.CompetitionCategoryLink
import core.competitions.kaggle.model.CompetitionCategoryLink.CompetitionCategoryLinkId
import postgres.Repository


private class CompetitionCategoryLinkTable(tag: Tag)
    extends Table[CompetitionCategoryLink](
      tag,
      Some(KaggleSchema.schemaName),
      "competition_category_links",
    ):

  def competitionId = column[Int]("competition_id")
  def categoryName  = column[String]("category_name")
  def updatedAt     = column[Instant]("updated_at")

  def pk = primaryKey("competition_category_links_pkey", (competitionId, categoryName))

  def * = (
    competitionId,
    categoryName,
    updatedAt,
  ).mapTo[CompetitionCategoryLink]

end CompetitionCategoryLinkTable


object CompetitionCategoryLinkRepository
    extends Repository[
      CompetitionCategoryLink,
      CompetitionCategoryLinkId,
      CompetitionCategoryLinkTable,
    ]:

  val tableQuery = TableQuery[CompetitionCategoryLinkTable]

  protected def idMatcher(
    id: CompetitionCategoryLinkId
  ): CompetitionCategoryLinkTable => Rep[Boolean] =
    t => t.competitionId === id.competitionId && t.categoryName === id.categoryName

end CompetitionCategoryLinkRepository
