package core.misc.gsoc.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.misc.gsoc.model.Organization.OrganizationId
import core.misc.gsoc.model.{ ContactLink, Organization }
import postgres.SlickExtensions.paginate
import postgres.{ Repository, SlickJsonStringColumnSupport }


// JSON support for ContactLink using upickle
object OrganizationJsonSupport:

  import upickle.default.{ ReadWriter, macroRW }

  // Define JSON serialization here to keep domain models free of serialization concerns
  // Using macroRW instead of adding `derives ReadWriter` to ContactLink
  // -- Li Bao wrote a reminder for his future self
  given ReadWriter[ContactLink] = macroRW

  given contactLinkListColumnType: BaseColumnType[List[ContactLink]] =
    SlickJsonStringColumnSupport.listColumnType[ContactLink]

end OrganizationJsonSupport


private class OrganizationTable(tag: Tag)
    extends Table[Organization](tag, Some(GsocSchema.schemaName), "organizations"):

  import OrganizationJsonSupport.contactLinkListColumnType
  import SlickJsonStringColumnSupport.stringListColumnType

  def year                   = column[Int]("year")
  def slug                   = column[String]("slug")
  def name                   = column[String]("name")
  def logoUrl                = column[String]("logo_url")
  def websiteUrl             = column[String]("website_url")
  def tagline                = column[String]("tagline")
  def license                = column[Option[String]]("license")
  def contributorGuidanceUrl = column[Option[String]]("contributor_guidance_url")
  def description            = column[Option[String]]("description")
  def techTags               = column[List[String]]("tech_tags")
  def topicTags              = column[List[String]]("topic_tags")
  def contactLinks           = column[List[ContactLink]]("contact_links")
  def sourceCode             = column[Option[String]]("source_code")
  def ideasLink              = column[Option[String]]("ideas_link")
  def updatedAt              = column[Instant]("updated_at")

  def pk = primaryKey("organizations_pkey", (year, slug))

  def idxName = index("organizations_name_idx", name)

  def * = (
    year,
    slug,
    name,
    logoUrl,
    websiteUrl,
    tagline,
    license,
    contributorGuidanceUrl,
    description,
    techTags,
    topicTags,
    contactLinks,
    sourceCode,
    ideasLink,
    updatedAt,
  ).mapTo[Organization]

end OrganizationTable


object OrganizationRepository extends Repository[Organization, OrganizationId, OrganizationTable]:

  protected val tableQuery = TableQuery[OrganizationTable]

  protected def idMatcher(id: OrganizationId): OrganizationTable => Rep[Boolean] =
    t => t.year === id.year && t.slug === id.slug

  /** Helper: Build filtered query by year and optional category */
  private def filterByYearAndCategory(
    year: Int,
    categoryFilter: Option[String],
  ): Query[OrganizationTable, Organization, Seq] =
    val baseQuery = tableQuery.filter(_.year === year)
    categoryFilter.fold(baseQuery) { categoryName =>
      baseQuery.filter(org =>
        OrganizationCategoriesRepository.tableQuery
          .filter(oc =>
            oc.year === org.year &&
              oc.slug === org.slug &&
              oc.name === categoryName
          )
          .exists
      )
    }
  end filterByYearAndCategory

  /** Finds organizations by year with categories and total count in parallel.
    */
  def findByYearPaginatedWithCategories(
    year: Int,
    categoryFilter: Option[String],
    limit: Int,
    offset: Int,
  ): Future[(Seq[(Organization, Seq[String])], Int)] =

    // 1. Base Query (Filtered)
    val baseQuery = filterByYearAndCategory(year, categoryFilter)

    // 2. Count Action
    val countAction = baseQuery.length.result

    // 3. Data Query Action (Paginate then Join)
    val fetchAction = baseQuery
      .sortBy(_.slug)
      .paginate(Some(limit), Some(offset))
      .joinLeft(OrganizationCategoriesRepository.tableQuery)
      .on { (org, orgCat) =>
        orgCat.year === org.year && orgCat.slug === org.slug
      }
      .map((org, orgCat) => (org, orgCat.map(_.name)))
      .result

    // 4. Run in parallel
    db.run(fetchAction.zip(countAction)).map { case (flatResults, totalCount) =>
      val grouped = flatResults
        .groupMap(_._1)(_._2)
        .view
        .mapValues(_.flatten) // Convert Seq[Option[String]] to Seq[String]
        .toSeq
        .sortBy(_._1.slug) // Re-apply sort in memory

      (grouped, totalCount)
    }
  end findByYearPaginatedWithCategories

end OrganizationRepository
