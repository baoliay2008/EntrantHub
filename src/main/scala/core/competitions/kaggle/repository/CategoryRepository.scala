package core.competitions.kaggle.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.competitions.kaggle.model.Category
import core.competitions.kaggle.model.Category.CategoryId
import postgres.Repository


private class CategoryTable(tag: Tag)
    extends Table[Category](tag, Some(KaggleSchema.schemaName), "categories"):

  def name             = column[String]("name", O.PrimaryKey)
  def id               = column[Option[Int]]("id")
  def displayName      = column[String]("display_name")
  def fullPath         = column[String]("full_path")
  def slug             = column[Option[String]]("slug")
  def description      = column[Option[String]]("description")
  def datasetCount     = column[Option[Int]]("dataset_count")
  def competitionCount = column[Option[Int]]("competition_count")
  def scriptCount      = column[Option[Int]]("script_count")
  def updatedAt        = column[Instant]("updated_at")

  def * = (
    name,
    id,
    displayName,
    fullPath,
    slug,
    description,
    datasetCount,
    competitionCount,
    scriptCount,
    updatedAt,
  ).mapTo[Category]

end CategoryTable


object CategoryRepository extends Repository[Category, CategoryId, CategoryTable]:

  val tableQuery = TableQuery[CategoryTable]

  protected def idMatcher(name: CategoryId): CategoryTable => Rep[Boolean] =
    _.name === name

end CategoryRepository
