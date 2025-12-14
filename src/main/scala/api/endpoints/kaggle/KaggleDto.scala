package api.endpoints.kaggle


import java.time.Instant

import upickle.default.Writer

import util.Serde.instantReadWriter


object KaggleDto:

  case class CategoryResponse(
    name: String,
    id: Option[Int],
    displayName: String,
    fullPath: String,
    slug: Option[String],
    description: Option[String],
  ) derives Writer

  case class OrganizationResponse(
    slug: String,
    name: String,
    thumbnailImageUrl: String,
    profileUrl: String,
  ) derives Writer

  case class CompetitionResponse(
    id: Int,
    competitionName: String,
    title: String,
    briefDescription: String,
    hostName: String,
    dateEnabled: Instant,
    deadline: Instant,
    maxTeamSize: Int,
    numPrizes: Option[Int],
    totalTeams: Option[Int],
    totalCompetitors: Option[Int],
    totalSubmissions: Option[Int],
    totalJoinedUsers: Option[Int],
    hasLeaderboard: Option[Boolean],
    rewardId: Option[String],
    rewardQuantity: Option[Int],
    organization: Option[OrganizationResponse],
    categories: List[CategoryResponse],
  ) derives Writer

end KaggleDto
