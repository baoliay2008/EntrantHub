package core.misc.gsoc.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Organization(
  year: Int,
  slug: String,
  name: String,
  logoUrl: String,
  websiteUrl: String,
  tagline: String,
  license: Option[String] = None,
  contributorGuidanceUrl: Option[String] = None,
  description: Option[String] = None,
  techTags: List[String],
  topicTags: List[String],
  contactLinks: List[ContactLink],
  sourceCode: Option[String] = None,
  ideasLink: Option[String] = None,
  updatedAt: Instant = Instant.now(),
)


case class ContactLink(
  name: String,
  value: String,
)


object Organization:

  type OrganizationId = (year: Int, slug: String)

  given EntityIdMapping[Organization, OrganizationId] with
    extension (o: Organization)
      def getId: OrganizationId = (o.year, o.slug)
