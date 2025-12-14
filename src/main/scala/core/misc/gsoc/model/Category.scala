package core.misc.gsoc.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Category(
  name: String,
  updatedAt: Instant = Instant.now(),
)


object Category:

  type CategoryId = String

  given EntityIdMapping[Category, CategoryId] with
    extension (c: Category)
      def getId: CategoryId = c.name
