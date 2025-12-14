package api.endpoints.kaggle


import KaggleDto.*
import core.competitions.kaggle.model.{ Category, Competition, Organization }


object KaggleMapper:

  private def toCategoryResponse(category: Category): CategoryResponse =
    CategoryResponse(
      name = category.name,
      id = category.id,
      displayName = category.displayName,
      fullPath = category.fullPath,
      slug = category.slug,
      description = category.description,
    )

  private def toOrganizationResponse(organization: Organization): OrganizationResponse =
    OrganizationResponse(
      slug = organization.slug,
      name = organization.name,
      thumbnailImageUrl = organization.thumbnailImageUrl,
      profileUrl = organization.profileUrl,
    )

  def toCompetitionResponse(
    competition: Competition,
    organization: Option[Organization],
    categories: List[Category],
  ): CompetitionResponse =
    CompetitionResponse(
      id = competition.id,
      competitionName = competition.competitionName,
      title = competition.title,
      briefDescription = competition.briefDescription,
      hostName = competition.hostName,
      dateEnabled = competition.dateEnabled,
      deadline = competition.deadline,
      maxTeamSize = competition.maxTeamSize,
      numPrizes = competition.numPrizes,
      totalTeams = competition.totalTeams,
      totalCompetitors = competition.totalCompetitors,
      totalSubmissions = competition.totalSubmissions,
      totalJoinedUsers = competition.totalJoinedUsers,
      hasLeaderboard = competition.hasLeaderboard,
      rewardId = competition.rewardId,
      rewardQuantity = competition.rewardQuantity,
      organization = organization.map(toOrganizationResponse),
      categories = categories.map(toCategoryResponse),
    )

end KaggleMapper
