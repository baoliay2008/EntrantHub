package api.endpoints.devpost


import java.time.{ Instant, LocalDate }

import upickle.default.Writer

import util.Serde.{ instantReadWriter, localDateReadWriter }


object DevPostDto:

  case class CategoryResponse(
    id: Int,
    name: String,
  ) derives Writer

  case class HackathonResponse(
    id: Int,
    title: String,
    location: String,
    openState: String,
    url: String,
    thumbnailUrl: String,
    submissionStartDate: LocalDate,
    submissionEndDate: LocalDate,
    prizeAmount: String,
    cashPrizesCount: Int,
    otherPrizesCount: Int,
    registrationsCount: Int,
    featured: Boolean,
    organizationName: Option[String],
    winnersAnnounced: Boolean,
    submissionGalleryUrl: String,
    startASubmissionUrl: String,
    inviteOnly: Boolean,
    inviteOnlyRequirement: Option[String],
    managedByDevpost: Boolean,
    categories: List[CategoryResponse],
  ) derives Writer

end DevPostDto
