package core.misc.gsoc.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Project(
  id: String, // uid for current projects, id for archived
  year: Int,
  title: String,
  programSlug: String,
  organizationName: String,
  organizationSlug: String,
  contributorName: String,
  contributorId: Option[String],
  mentors: List[String],
  description: String,      // body for current, abstract_html for archived
  shortDescription: String, // body_short for current, abstract_short for archived
  techTags: List[String],
  topicTags: List[String],
  size: Option[String],       // Only for current projects
  status: Option[String],     // Only for current projects
  phase: Option[String],      // Only for current projects
  proposalId: Option[String], // Only for current projects
  projectCodeUrl: Option[String],
  dateCreated: Instant,
  dateUpdated: Option[Instant],  // date_updated for current, date_archived for archived
  dateUploaded: Option[Instant], // Only for current projects
  updatedAt: Instant = Instant.now(),
)


object Project:
  type ProjectId = String

  given EntityIdMapping[Project, ProjectId] with
    extension (p: Project)
      def getId: ProjectId = p.id
