package core.misc.gsoc.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.misc.gsoc.model.Category
import core.misc.gsoc.model.Category.CategoryId
import postgres.Repository


private class CategoryTable(tag: Tag)
    extends Table[Category](tag, Some(GsocSchema.schemaName), "categories"):

  def name      = column[String]("name", O.PrimaryKey)
  def updatedAt = column[Instant]("updated_at")

  def * = (
    name,
    updatedAt,
  ).mapTo[Category]

end CategoryTable


object CategoryRepository extends Repository[Category, CategoryId, CategoryTable]:

  protected val tableQuery = TableQuery[CategoryTable]

  protected def idMatcher(name: CategoryId): CategoryTable => Rep[Boolean] =
    _.name === name

end CategoryRepository
