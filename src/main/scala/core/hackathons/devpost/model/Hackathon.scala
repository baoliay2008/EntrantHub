package core.hackathons.devpost.model


import java.time.{ Instant, LocalDate }

import util.TypeClasses.EntityIdMapping


case class Hackathon(
  id: Int,
  title: String,
  location: String,
  openState: String, // open, upcoming, ended
  url: String,
  thumbnailUrl: String,
  submissionStartDate: LocalDate,
  submissionEndDate: LocalDate,
  prizeAmount: String, // remove html span tag
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
  updatedAt: Instant = Instant.now(),
)


object Hackathon:

  type HackathonId = Int

  given EntityIdMapping[Hackathon, HackathonId] with
    extension (h: Hackathon)
      def getId: HackathonId = h.id
