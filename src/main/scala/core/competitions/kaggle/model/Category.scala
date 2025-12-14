package core.competitions.kaggle.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Category(
  name: String,           // WARN: This is true ID
  id: Option[Int] = None, // WARN: It's nullable, but it's still useful
  displayName: String,
  fullPath: String,
  slug: Option[String] = None,
  description: Option[String] = None,
  datasetCount: Option[Int] = None,
  competitionCount: Option[Int] = None,
  scriptCount: Option[Int] = None,
  updatedAt: Instant = Instant.now(),
)


object Category:

  type CategoryId = String

  given EntityIdMapping[Category, CategoryId] with
    extension (c: Category)
      def getId: CategoryId = c.name
