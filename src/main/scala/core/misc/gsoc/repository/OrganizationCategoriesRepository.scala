package core.misc.gsoc.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.misc.gsoc.model.OrganizationCategories
import core.misc.gsoc.model.OrganizationCategories.OrganizationCategoriesId
import postgres.Repository


private class OrganizationCategoriesTable(tag: Tag)
    extends Table[OrganizationCategories](
      tag,
      Some(GsocSchema.schemaName),
      "organization_categories",
    ):

  def year      = column[Int]("year")
  def slug      = column[String]("slug")
  def name      = column[String]("name")
  def updatedAt = column[Instant]("updated_at")

  def pk = primaryKey("organization_categories_pkey", (year, slug, name))

  def * = (
    year,
    slug,
    name,
    updatedAt,
  ).mapTo[OrganizationCategories]

end OrganizationCategoriesTable


object OrganizationCategoriesRepository
    extends Repository[
      OrganizationCategories,
      OrganizationCategoriesId,
      OrganizationCategoriesTable,
    ]:

  val tableQuery = TableQuery[OrganizationCategoriesTable]

  protected def idMatcher(
    id: OrganizationCategoriesId
  ): OrganizationCategoriesTable => Rep[Boolean] =
    t => t.year === id.year && t.slug === id.slug && t.name === id.name

end OrganizationCategoriesRepository
