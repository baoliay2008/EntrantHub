package core.misc.gsoc.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class OrganizationCategories(
  year: Int,
  slug: String,
  name: String,
  updatedAt: Instant = Instant.now(),
)


object OrganizationCategories:

  type OrganizationCategoriesId = (year: Int, slug: String, name: String)

  given EntityIdMapping[OrganizationCategories, OrganizationCategoriesId] with
    extension (oc: OrganizationCategories)
      def getId: OrganizationCategoriesId = (oc.year, oc.slug, oc.name)
