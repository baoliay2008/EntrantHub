package api.endpoints.gsoc


import GsocDto.*
import core.misc.gsoc.model.{ ContactLink, Milestone, Organization, Project }


object GsocMapper:

  private def toContactLinkResponse(contactLink: ContactLink): ContactLinkResponse =
    ContactLinkResponse(
      name = contactLink.name,
      value = contactLink.value,
    )

  def toOrganizationResponse(
    org: Organization,
    categories: List[String],
  ): OrganizationResponse =
    OrganizationResponse(
      year = org.year,
      slug = org.slug,
      name = org.name,
      logoUrl = org.logoUrl,
      websiteUrl = org.websiteUrl,
      tagline = org.tagline,
      categories = categories,
      license = org.license,
      contributorGuidanceUrl = org.contributorGuidanceUrl,
      description = org.description,
      techTags = org.techTags,
      topicTags = org.topicTags,
      contactLinks = org.contactLinks.map(toContactLinkResponse),
      sourceCode = org.sourceCode,
      ideasLink = org.ideasLink,
    )

  private def toMilestoneResponse(milestone: Milestone): MilestoneResponse =
    MilestoneResponse(
      name = milestone.name,
      datetime = milestone.datetime,
      phase = milestone.phase,
    )

  def toProjectResponse(
    project: Project,
    milestones: List[Milestone],
  ): ProjectResponse =
    ProjectResponse(
      id = project.id,
      year = project.year,
      title = project.title,
      programSlug = project.programSlug,
      organizationName = project.organizationName,
      organizationSlug = project.organizationSlug,
      contributorName = project.contributorName,
      contributorId = project.contributorId,
      mentors = project.mentors,
      description = project.description,
      shortDescription = project.shortDescription,
      techTags = project.techTags,
      topicTags = project.topicTags,
      size = project.size,
      status = project.status,
      phase = project.phase,
      proposalId = project.proposalId,
      projectCodeUrl = project.projectCodeUrl,
      dateCreated = project.dateCreated,
      dateUpdated = project.dateUpdated,
      dateUploaded = project.dateUploaded,
      milestones = milestones.map(toMilestoneResponse),
    )

end GsocMapper
