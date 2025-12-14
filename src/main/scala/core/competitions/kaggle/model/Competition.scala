package core.competitions.kaggle.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Competition(
  id: Int,
  competitionHostSegmentId: Int,
  hostName: String,
  competitionName: String,
  title: String,
  briefDescription: String,
  dateEnabled: Instant,
  deadline: Instant,
  maxTeamSize: Int,
  numPrizes: Option[Int] = None,
  totalTeams: Option[Int] = None,
  totalCompetitors: Option[Int] = None,
  totalSubmissions: Option[Int] = None,
  totalJoinedUsers: Option[Int] = None,
  hasLeaderboard: Option[Boolean] = None,
  rewardId: Option[String] = None,         // Expanded nested Reward field
  rewardQuantity: Option[Int] = None,      // Expanded nested Reward field
  organizationSlug: Option[String] = None, // Organization.slug
  updatedAt: Instant = Instant.now(),
)


object Competition:

  type CompetitionId = Int

  given EntityIdMapping[Competition, CompetitionId] with
    extension (c: Competition)
      def getId: CompetitionId = c.id
