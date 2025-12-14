package core.hackathons.devpost.repository


import java.time.{ Instant, LocalDate }

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.hackathons.devpost.model.Hackathon.HackathonId
import core.hackathons.devpost.model.{ Category, Hackathon }
import postgres.Repository


private class HackathonTable(tag: Tag)
    extends Table[Hackathon](tag, Some(DevPostSchema.schemaName), "hackathons"):

  def id                    = column[Int]("id", O.PrimaryKey)
  def title                 = column[String]("title")
  def location              = column[String]("location")
  def openState             = column[String]("open_state")
  def url                   = column[String]("url")
  def thumbnailUrl          = column[String]("thumbnail_url")
  def submissionStartDate   = column[LocalDate]("submission_start_date")
  def submissionEndDate     = column[LocalDate]("submission_end_date")
  def prizeAmount           = column[String]("prize_amount")
  def cashPrizesCount       = column[Int]("cash_prizes_count")
  def otherPrizesCount      = column[Int]("other_prizes_count")
  def registrationsCount    = column[Int]("registrations_count")
  def featured              = column[Boolean]("featured")
  def organizationName      = column[Option[String]]("organization_name")
  def winnersAnnounced      = column[Boolean]("winners_announced")
  def submissionGalleryUrl  = column[String]("submission_gallery_url")
  def startASubmissionUrl   = column[String]("start_a_submission_url")
  def inviteOnly            = column[Boolean]("invite_only")
  def inviteOnlyRequirement = column[Option[String]]("invite_only_requirement")
  def managedByDevpost      = column[Boolean]("managed_by_devpost")
  def updatedAt             = column[Instant]("updated_at")

  def idxTitle              = index("hackathons_title_idx", title)
  def idxOrganizationName   = index("hackathons_organization_name_idx", organizationName)
  def idxRegistrationsCount = index("hackathons_registrations_count_idx", registrationsCount)
  def idxSubmissionEndDate  = index("hackathons_submission_end_date_idx", submissionEndDate)
  def idxSubmissionDates = index(
    "hackathons_submission_dates_idx",
    (submissionStartDate, submissionEndDate),
  )

  def * = (
    id,
    title,
    location,
    openState,
    url,
    thumbnailUrl,
    submissionStartDate,
    submissionEndDate,
    prizeAmount,
    cashPrizesCount,
    otherPrizesCount,
    registrationsCount,
    featured,
    organizationName,
    winnersAnnounced,
    submissionGalleryUrl,
    startASubmissionUrl,
    inviteOnly,
    inviteOnlyRequirement,
    managedByDevpost,
    updatedAt,
  ).mapTo[Hackathon]

end HackathonTable


object HackathonRepository extends Repository[Hackathon, HackathonId, HackathonTable]:

  protected val tableQuery = TableQuery[HackathonTable]

  protected def idMatcher(id: HackathonId): HackathonTable => Rep[Boolean] =
    _.id === id

  /** Extension method for dynamic sorting on hackathon queries */
  extension (query: Query[HackathonTable, Hackathon, Seq])
    def sortByField(sortBy: String, sortOrder: String): Query[HackathonTable, Hackathon, Seq] =
      val isDesc = sortOrder == "desc"

      sortBy match
        case "submissionEndDate" =>
          query.sortBy(t => if isDesc then t.submissionEndDate.desc else t.submissionEndDate.asc)
        case "submissionStartDate" =>
          query.sortBy(t =>
            if isDesc then t.submissionStartDate.desc else t.submissionStartDate.asc
          )
        case "registrationsCount" =>
          query.sortBy(t =>
            if isDesc then t.registrationsCount.desc else t.registrationsCount.asc
          )
        case "title" =>
          query.sortBy(t => if isDesc then t.title.desc else t.title.asc)
        case _ =>
          query.sortBy(_.submissionEndDate.desc) // Default
      end match
    end sortByField
  end extension

  /** Helper: Build filtered query using Slick's filterOpt */
  private def filterQuery(
    categoryIdFilter: Option[Int],
    openStateFilter: Option[String],
  ): Query[HackathonTable, Hackathon, Seq] =
    tableQuery
      .filterOpt(openStateFilter)((t, state) => t.openState === state)
      .filterOpt(categoryIdFilter) { (t, categoryId) =>
        HackathonCategoryLinkRepository.tableQuery
          .filter(link =>
            link.hackathonId === t.id &&
              link.categoryId === categoryId
          )
          .exists
      }
  end filterQuery

  /** Finds hackathons with categories and total count in a single parallel DB operation.
    */
  def findPaginatedWithCategories(
    categoryIdFilter: Option[Int],
    openStateFilter: Option[String],
    sortBy: String,
    sortOrder: String,
    limit: Int,
    offset: Int,
  ): Future[(Seq[(Hackathon, Seq[Category])], Int)] =
    import postgres.SlickExtensions.paginate

    // 1. Base Query (Filtered)
    val baseQuery = filterQuery(categoryIdFilter, openStateFilter)

    // 2. Count Query (Action)
    val countAction = baseQuery.length.result

    // 3. Data Query (Action) - Filter -> Sort -> Paginate -> Join
    val paginatedQuery = baseQuery
      .sortByField(sortBy, sortOrder)
      .paginate(Some(limit), Some(offset))

    val joinQuery = paginatedQuery
      .joinLeft(HackathonCategoryLinkRepository.tableQuery)
      .on(_.id === _.hackathonId)
      .joinLeft(CategoryRepository.tableQuery)
      .on(_._2.map(_.categoryId) === _.id)
      .map { case ((hackathon, _), category) => (hackathon, category) }

    val fetchAction = joinQuery.result

    // 4. Zip Actions (Run in parallel in DB) and Map results
    db.run(fetchAction.zip(countAction)).map { case (flatResults, totalCount) =>

      // Grouping Logic (in memory)
      val grouped = flatResults
        .groupMap(_._1)(_._2)
        .view
        .mapValues(_.flatten)
        .toSeq

      // Re-apply sort in memory (groupMap destroys order)
      val sortedHackathons = sortBy match
        case "registrationsCount" =>
          if sortOrder == "desc" then
            grouped.sortBy(-_._1.registrationsCount)
          else
            grouped.sortBy(_._1.registrationsCount)
        case "submissionEndDate" =>
          if sortOrder == "desc" then
            grouped.sortBy(-_._1.submissionEndDate.toEpochDay)
          else
            grouped.sortBy(_._1.submissionEndDate.toEpochDay)
        case "submissionStartDate" =>
          if sortOrder == "desc" then
            grouped.sortBy(-_._1.submissionStartDate.toEpochDay)
          else
            grouped.sortBy(_._1.submissionStartDate.toEpochDay)
        case "title" =>
          if sortOrder == "desc" then
            grouped.sortBy(_._1.title)(
              using Ordering[String].reverse
            )
          else
            grouped.sortBy(_._1.title)
        case _ =>
          grouped.sortBy(-_._1.submissionEndDate.toEpochDay)

      (sortedHackathons, totalCount)
    }
  end findPaginatedWithCategories

  /** Find a single hackathon by ID with its categories */
  def findByIdWithCategories(
    id: Int
  ): Future[Option[(Hackathon, Seq[Category])]] =
    val query = tableQuery
      .filter(_.id === id)
      .joinLeft(HackathonCategoryLinkRepository.tableQuery)
      .on(_.id === _.hackathonId)
      .joinLeft(CategoryRepository.tableQuery)
      .on(_._2.map(_.categoryId) === _.id)
      .map { case ((hackathon, _), category) =>
        (hackathon, category)
      }

    db.run(query.result).map { results =>
      if results.isEmpty then
        None
      else
        val hackathon  = results.head._1
        val categories = results.flatMap(_._2)
        Some((hackathon, categories))
    }
  end findByIdWithCategories

end HackathonRepository
