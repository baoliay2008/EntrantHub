package core.hackathons.devpost.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Category( // themes array
  id: Int,
  name: String,
  updatedAt: Instant = Instant.now(),
)


object Category:
  type CategoryId = Int

  given EntityIdMapping[Category, CategoryId] with
    extension (c: Category)
      def getId: CategoryId = c.id
