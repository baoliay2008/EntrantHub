package core.competitions.kaggle.repository


import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.competitions.kaggle.model.Competition.CompetitionId
import core.competitions.kaggle.model.{ Category, Competition, Organization }
import postgres.Repository


private class CompetitionTable(tag: Tag)
    extends Table[Competition](tag, Some(KaggleSchema.schemaName), "competitions"):

  def id                       = column[Int]("id", O.PrimaryKey)
  def competitionHostSegmentId = column[Int]("competition_host_segment_id")
  def hostName                 = column[String]("host_name")
  def competitionName          = column[String]("competition_name")
  def title                    = column[String]("title")
  def briefDescription         = column[String]("brief_description")
  def dateEnabled              = column[Instant]("date_enabled")
  def deadline                 = column[Instant]("deadline")
  def maxTeamSize              = column[Int]("max_team_size")
  def numPrizes                = column[Option[Int]]("num_prizes")
  def totalTeams               = column[Option[Int]]("total_teams")
  def totalCompetitors         = column[Option[Int]]("total_competitors")
  def totalSubmissions         = column[Option[Int]]("total_submissions")
  def totalJoinedUsers         = column[Option[Int]]("total_joined_users")
  def hasLeaderboard           = column[Option[Boolean]]("has_leaderboard")
  def rewardId                 = column[Option[String]]("reward_id")
  def rewardQuantity           = column[Option[Int]]("reward_quantity")
  def organizationSlug         = column[Option[String]]("organization_slug")
  def updatedAt                = column[Instant]("updated_at")

  def idxCompetitionName  = index("competitions_name_key", competitionName, unique = true)
  def idxDateEnabled      = index("competitions_date_enabled_idx", dateEnabled)
  def idxDeadline         = index("competitions_deadline_idx", deadline)
  def idxOrganizationSlug = index("competitions_organization_slug_idx", organizationSlug)
  // Indexes for sorting
  def idxTitle            = index("competitions_title_idx", title)
  def idxTotalCompetitors = index("competitions_total_competitors_idx", totalCompetitors)
  def idxTotalSubmissions = index("competitions_total_submissions_idx", totalSubmissions)

  def * = (
    id,
    competitionHostSegmentId,
    hostName,
    competitionName,
    title,
    briefDescription,
    dateEnabled,
    deadline,
    maxTeamSize,
    numPrizes,
    totalTeams,
    totalCompetitors,
    totalSubmissions,
    totalJoinedUsers,
    hasLeaderboard,
    rewardId,
    rewardQuantity,
    organizationSlug,
    updatedAt,
  ).mapTo[Competition]

end CompetitionTable


object CompetitionRepository extends Repository[Competition, CompetitionId, CompetitionTable]:

  protected val tableQuery = TableQuery[CompetitionTable]

  protected def idMatcher(id: CompetitionId): CompetitionTable => Rep[Boolean] =
    _.id === id

  /** Extension method for dynamic sorting on competition queries */
  extension (query: Query[CompetitionTable, Competition, Seq])
    def sortByField(sortBy: String, sortOrder: String): Query[CompetitionTable, Competition, Seq] =
      val isDesc = sortOrder == "desc"

      sortBy match
        case "deadline" =>
          query.sortBy(t => if isDesc then t.deadline.desc else t.deadline.asc)
        case "dateEnabled" =>
          query.sortBy(t => if isDesc then t.dateEnabled.desc else t.dateEnabled.asc)
        case "title" =>
          query.sortBy(t => if isDesc then t.title.desc else t.title.asc)
        case "totalCompetitors" =>
          // Use nullsLast to ensure NULLs appear at the end for both ASC and DESC
          query.sortBy { t =>
            if isDesc then
              t.totalCompetitors.desc.nullsLast
            else
              t.totalCompetitors.asc.nullsLast
          }
        case "totalSubmissions" =>
          // Use nullsLast to ensure NULLs appear at the end for both ASC and DESC
          query.sortBy { t =>
            if isDesc then
              t.totalSubmissions.desc.nullsLast
            else
              t.totalSubmissions.asc.nullsLast
          }
        case _ =>
          query.sortBy(_.deadline.desc) // Default
      end match
    end sortByField
  end extension

  /** Reusable base query for recent competitions */
  private def recentCompetitionsQuery(): Query[CompetitionTable, Competition, Seq] =
    val oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS)
    tableQuery
      // Show competitions that ended within last month or are still active
      .filter(_.deadline > oneMonthAgo)

  /** Finds recent competitions with details and total count in parallel.
    */
  def findRecentPaginated(
    sortBy: String,
    sortOrder: String,
    limit: Int,
    offset: Int,
  ): Future[(Seq[(Competition, Option[Organization], Seq[Category])], Int)] =
    import postgres.SlickExtensions.paginate

    // 1. Base Query
    val baseQuery = recentCompetitionsQuery()

    // 2. Count Action
    val countAction = baseQuery.length.result

    // 3. Fetch Action
    val fetchAction = baseQuery
      .sortByField(sortBy, sortOrder)
      .paginate(Some(limit), Some(offset))
      .joinLeft(OrganizationRepository.tableQuery)
      .on(_.organizationSlug === _.slug)
      .joinLeft(CompetitionCategoryLinkRepository.tableQuery)
      .on(_._1.id === _.competitionId)
      .joinLeft(CategoryRepository.tableQuery)
      .on(_._2.map(_.categoryName) === _.name)
      .map { case (((competition, organization), _), category) =>
        (competition, organization, category)
      }
      .result

    // 4. Execute in Parallel
    db.run(fetchAction.zip(countAction)).map { case (flatResults, totalCount) =>

      // Grouping logic (InMemory)
      val grouped = flatResults
        .groupBy(_._1) // Group by Competition
        .view
        .mapValues { rows =>
          val org =
            rows.headOption.flatMap(_._2) // Organization is same for all rows of a competition
          val cats = rows.flatMap(_._3) // Collect all categories
          (org, cats)
        }
        .toSeq
        .map { case (comp, (org, cats)) => (comp, org, cats) }

      // Re-apply the sort after grouping (since groupMap loses SQL order)
      val sorted = sortBy match
        case "deadline" =>
          if sortOrder == "desc" then
            grouped.sortBy(-_._1.deadline.getEpochSecond)
          else
            grouped.sortBy(_._1.deadline.getEpochSecond)
        case "dateEnabled" =>
          if sortOrder == "desc" then
            grouped.sortBy(-_._1.dateEnabled.getEpochSecond)
          else
            grouped.sortBy(_._1.dateEnabled.getEpochSecond)
        case "totalCompetitors" =>
          // Sort with nulls last: first by isEmpty (false < true), then by value
          if sortOrder == "desc" then
            grouped.sortBy(g =>
              (g._1.totalCompetitors.isEmpty, -g._1.totalCompetitors.getOrElse(0))
            )
          else
            grouped.sortBy(g => (g._1.totalCompetitors.isEmpty, g._1.totalCompetitors.getOrElse(0)))
        case "totalSubmissions" =>
          // Sort with nulls last: first by isEmpty (false < true), then by value
          if sortOrder == "desc" then
            grouped.sortBy(g =>
              (g._1.totalSubmissions.isEmpty, -g._1.totalSubmissions.getOrElse(0))
            )
          else
            grouped.sortBy(g => (g._1.totalSubmissions.isEmpty, g._1.totalSubmissions.getOrElse(0)))
        case "title" =>
          if sortOrder == "desc" then
            grouped.sortBy(_._1.title)(
              using Ordering[String].reverse
            )
          else
            grouped.sortBy(_._1.title)
        case _ =>
          grouped.sortBy(-_._1.deadline.getEpochSecond)

      (sorted, totalCount)
    }
  end findRecentPaginated

  def findById(
    id: Int
  ): Future[Option[(Competition, Option[Organization], Seq[Category])]] =
    val query = tableQuery
      .filter(_.id === id)
      .joinLeft(OrganizationRepository.tableQuery)
      .on(_.organizationSlug === _.slug)
      .joinLeft(CompetitionCategoryLinkRepository.tableQuery)
      .on(_._1.id === _.competitionId)
      .joinLeft(CategoryRepository.tableQuery)
      .on(_._2.map(_.categoryName) === _.name)
      .map { case (((competition, organization), _), category) =>
        (competition, organization, category)
      }

    db.run(query.result).map { results =>
      if results.isEmpty then
        None
      else
        val competition  = results.head._1
        val organization = results.head._2
        val categories   = results.flatMap(_._3)
        Some((competition, organization, categories))
    }
  end findById

end CompetitionRepository
