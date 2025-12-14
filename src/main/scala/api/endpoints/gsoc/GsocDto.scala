package api.endpoints.gsoc


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object GsocDto:

  case class ContactLinkResponse(
    name: String,
    value: String,
  ) derives Writer

  case class OrganizationResponse(
    year: Int,
    slug: String,
    name: String,
    logoUrl: String,
    websiteUrl: String,
    tagline: String,
    categories: List[String],
    license: Option[String],
    contributorGuidanceUrl: Option[String],
    description: Option[String],
    techTags: List[String],
    topicTags: List[String],
    contactLinks: List[ContactLinkResponse],
    sourceCode: Option[String],
    ideasLink: Option[String],
  ) derives Writer

  case class MilestoneResponse(
    name: String,
    datetime: Instant,
    phase: String,
  ) derives Writer

  case class ProjectResponse(
    id: String,
    year: Int,
    title: String,
    programSlug: String,
    organizationName: String,
    organizationSlug: String,
    contributorName: String,
    contributorId: Option[String],
    mentors: List[String],
    description: String,
    shortDescription: String,
    techTags: List[String],
    topicTags: List[String],
    size: Option[String],
    status: Option[String],
    phase: Option[String],
    proposalId: Option[String],
    projectCodeUrl: Option[String],
    dateCreated: Instant,
    dateUpdated: Option[Instant],
    dateUploaded: Option[Instant],
    milestones: List[MilestoneResponse],
  ) derives Writer

end GsocDto
