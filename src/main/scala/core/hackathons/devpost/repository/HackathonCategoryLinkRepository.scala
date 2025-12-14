package core.hackathons.devpost.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.hackathons.devpost.model.HackathonCategoryLink
import core.hackathons.devpost.model.HackathonCategoryLink.HackathonCategoryLinkId
import postgres.Repository


private class HackathonCategoryLinkTable(tag: Tag)
    extends Table[HackathonCategoryLink](
      tag,
      Some(DevPostSchema.schemaName),
      "hackathon_category_links",
    ):

  def hackathonId = column[Int]("hackathon_id")
  def categoryId  = column[Int]("category_id")
  def updatedAt   = column[Instant]("updated_at")

  def pk = primaryKey("hackathon_category_links_pkey", (hackathonId, categoryId))

  def * = (
    hackathonId,
    categoryId,
    updatedAt,
  ).mapTo[HackathonCategoryLink]

end HackathonCategoryLinkTable


object HackathonCategoryLinkRepository
    extends Repository[
      HackathonCategoryLink,
      HackathonCategoryLinkId,
      HackathonCategoryLinkTable,
    ]:

  val tableQuery = TableQuery[HackathonCategoryLinkTable]

  protected def idMatcher(
    id: HackathonCategoryLinkId
  ): HackathonCategoryLinkTable => Rep[Boolean] =
    t => t.hackathonId === id.hackathonId && t.categoryId === id.categoryId

end HackathonCategoryLinkRepository
