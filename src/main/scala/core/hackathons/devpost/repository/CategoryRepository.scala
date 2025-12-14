package core.hackathons.devpost.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.hackathons.devpost.model.Category
import core.hackathons.devpost.model.Category.CategoryId
import postgres.Repository


private class CategoryTable(tag: Tag)
    extends Table[Category](tag, Some(DevPostSchema.schemaName), "categories"):

  def id        = column[Int]("id", O.PrimaryKey)
  def name      = column[String]("name")
  def updatedAt = column[Instant]("updated_at")

  def idxName = index("categories_name_key", name, unique = true)

  def * = (
    id,
    name,
    updatedAt,
  ).mapTo[Category]

end CategoryTable


object CategoryRepository extends Repository[Category, CategoryId, CategoryTable]:

  val tableQuery = TableQuery[CategoryTable]

  protected def idMatcher(id: CategoryId): CategoryTable => Rep[Boolean] =
    _.id === id

end CategoryRepository
