package core.competitions.kaggle.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Organization(
  slug: String, // ID
  name: String,
  thumbnailImageUrl: String,
  profileUrl: String,
  updatedAt: Instant = Instant.now(),
)


object Organization:

  type OrganizationId = String

  given EntityIdMapping[Organization, OrganizationId] with
    extension (c: Organization)
      def getId: OrganizationId = c.slug
