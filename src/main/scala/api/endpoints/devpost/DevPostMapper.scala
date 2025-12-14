package api.endpoints.devpost


import DevPostDto.*
import core.hackathons.devpost.model.{ Category, Hackathon }


object DevPostMapper:

  private def toCategoryResponse(category: Category): CategoryResponse =
    CategoryResponse(
      id = category.id,
      name = category.name,
    )

  def toHackathonResponse(
    hackathon: Hackathon,
    categories: List[Category],
  ): HackathonResponse =
    HackathonResponse(
      id = hackathon.id,
      title = hackathon.title,
      location = hackathon.location,
      openState = hackathon.openState,
      url = hackathon.url,
      thumbnailUrl = hackathon.thumbnailUrl,
      submissionStartDate = hackathon.submissionStartDate,
      submissionEndDate = hackathon.submissionEndDate,
      prizeAmount = hackathon.prizeAmount,
      cashPrizesCount = hackathon.cashPrizesCount,
      otherPrizesCount = hackathon.otherPrizesCount,
      registrationsCount = hackathon.registrationsCount,
      featured = hackathon.featured,
      organizationName = hackathon.organizationName,
      winnersAnnounced = hackathon.winnersAnnounced,
      submissionGalleryUrl = hackathon.submissionGalleryUrl,
      startASubmissionUrl = hackathon.startASubmissionUrl,
      inviteOnly = hackathon.inviteOnly,
      inviteOnlyRequirement = hackathon.inviteOnlyRequirement,
      managedByDevpost = hackathon.managedByDevpost,
      categories = categories.map(toCategoryResponse),
    )

  def toCategoryResponseList(categories: Seq[Category]): List[CategoryResponse] =
    categories.map(toCategoryResponse).toList

end DevPostMapper
