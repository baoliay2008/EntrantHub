package core.competitions.kaggle.repository


import java.time.Instant

import slick.jdbc.PostgresProfile.api.*

import core.competitions.kaggle.model.Organization
import core.competitions.kaggle.model.Organization.OrganizationId
import postgres.Repository


private class OrganizationTable(tag: Tag)
    extends Table[Organization](tag, Some(KaggleSchema.schemaName), "organizations"):

  def slug              = column[String]("slug", O.PrimaryKey)
  def name              = column[String]("name")
  def thumbnailImageUrl = column[String]("thumbnail_image_url")
  def profileUrl        = column[String]("profile_url")
  def updatedAt         = column[Instant]("updated_at")

  def * = (
    slug,
    name,
    thumbnailImageUrl,
    profileUrl,
    updatedAt,
  ).mapTo[Organization]

end OrganizationTable


object OrganizationRepository extends Repository[Organization, OrganizationId, OrganizationTable]:

  val tableQuery = TableQuery[OrganizationTable]

  protected def idMatcher(id: OrganizationId): OrganizationTable => Rep[Boolean] =
    _.slug === id

end OrganizationRepository
