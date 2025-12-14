package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.Ranking.RankingId
import core.contests.leetcode.model.User.UserId
import core.contests.leetcode.model.{ Contest, Ranking }
import postgres.SlickPlainSqlSupport.given
import postgres.StageableRepository


private class RankingTable(tag: Tag, tableName: String = "rankings")
    extends Table[Ranking](tag, Some(LeetCodeSchema.schemaName), tableName):

  def contestTitleSlug      = column[String]("contest_title_slug")
  def dataRegion            = column[String]("data_region")
  def userSlug              = column[String]("user_slug")
  def rank                  = column[Int]("rank")
  def score                 = column[Int]("score")
  def finishTime            = column[Instant]("finish_time")
  def attendedContestsCount = column[Option[Int]]("attended_contests_count")
  def oldRating             = column[Option[Double]]("old_rating")
  def expectedRating        = column[Option[Double]]("expected_rating")
  def deltaRating           = column[Option[Double]]("delta_rating")
  def updatedAt             = column[Instant]("updated_at")

  def pk = primaryKey("rankings_pkey", (contestTitleSlug, userSlug, dataRegion))

  def idxContestRank = index("rankings_contest_title_slug_rank_idx", (contestTitleSlug, rank))
  def idxUserSlug    = index("rankings_user_slug_idx", userSlug)

  def * = (
    contestTitleSlug,
    dataRegion,
    userSlug,
    rank,
    score,
    finishTime,
    attendedContestsCount,
    oldRating,
    expectedRating,
    deltaRating,
    updatedAt,
  ).mapTo[Ranking]

end RankingTable


object RankingRepository extends StageableRepository[Ranking, RankingId, RankingTable]:

  protected def customTableQuery(name: String): TableQuery[RankingTable] =
    TableQuery[RankingTable](tag => RankingTable(tag, name))

  protected val tableQuery = TableQuery[RankingTable]

  protected def idMatcher(id: RankingId): RankingTable => Rep[Boolean] =
    rankingTable =>
      rankingTable.contestTitleSlug === id.contestTitleSlug &&
        rankingTable.dataRegion === id.dataRegion &&
        rankingTable.userSlug === id.userSlug

  def findByContestPaginated(
    contestTitleSlug: String,
    limit: Int,
    offset: Int,
  ): Future[(Seq[Ranking], Int)] =
    import postgres.SlickExtensions.paginate

    // 1. Base Query
    val baseQuery = tableQuery
      .filter(t => t.contestTitleSlug === contestTitleSlug && t.score > 0)

    // 2. Create Actions
    val countAction = baseQuery
      .length
      .result
    val fetchAction = baseQuery
      .sortBy(_.rank.asc)
      .paginate(Some(limit), Some(offset))
      .result

    // 3. Run in Parallel
    db.run(fetchAction.zip(countAction))
  end findByContestPaginated

  def findByContest(contestTitleSlug: String): Future[Seq[Ranking]] =
    find(
      where = Some(t => t.contestTitleSlug === contestTitleSlug && t.score > 0),
      orderBy = Some(_.rank.asc),
    )

  def findUserIdsByContest(
    contestTitleSlug: String,
    positiveScoreOnly: Boolean,
  ): Future[Seq[UserId]] =
    findColumns(
      select = t => (t.dataRegion, t.userSlug),
      where = Some { t =>
        val baseFilter = t.contestTitleSlug === contestTitleSlug
        if positiveScoreOnly then
          baseFilter && (t.score > 0)
        else
          baseFilter
      },
      orderBy = Some(_.rank.asc),
    )

  def bulkSyncByContest(
    contestTitleSlug: String,
    rankings: Seq[Ranking],
  ): Future[Int] =
    bulkSync(rankings, _.contestTitleSlug === contestTitleSlug)

  /** Populate `attended_contests_count` and `old_rating` for a given contest.
    *
    *   1. Pulls each user's current attended_contests_count + rating from the `users` table.
    *   1. For any remaining NULLs, fills in `defaultContestsCount` and `defaultRating`.
    *   1. If there is a biweekly contest 12 hours before this weekly, adds that contest's
    *      `delta_rating` to the new `old_rating`, but only for users who have both entries in
    *      `rankings`.
    *
    * @return
    *   total number of rows updated
    */
  def fillUserOldRatingInfo(
    contestTitleSlug: String,
    defaultContestsCount: Int = Ranking.DefaultAttendedContestsCount,
    defaultRating: Double = Ranking.DefaultRating,
  ): Future[Int] =

    // 1. Base update from users table
    val updateFromUsers: DBIO[Int] =
      sqlu"""
        UPDATE #$fullTableName r
        SET attended_contests_count = u.attended_contests_count,
            old_rating              = u.rating
        FROM #${UserRepository.fullTableName} u
        WHERE r.contest_title_slug = $contestTitleSlug
          AND r.data_region         = u.data_region
          AND r.user_slug           = u.user_slug
      """

    // 2. Fill any remaining NULLs with defaults
    val fillDefaults: DBIO[Int] =
      sqlu"""
        UPDATE #$fullTableName r
        SET attended_contests_count = $defaultContestsCount,
            old_rating              = $defaultRating
        WHERE r.contest_title_slug = $contestTitleSlug
          AND (r.attended_contests_count IS NULL OR r.old_rating IS NULL)
      """

    // 3. If there's a biweekly contest 12 h earlier, add its deltaRating
    val prevSlugOpt = Contest.precedingBiweeklySlugForOddWeek(contestTitleSlug)

    val updateWithPrevDelta: DBIO[Int] = prevSlugOpt match
      case Some(prevSlug) =>
        sqlu"""
          UPDATE #$fullTableName r
          SET attended_contests_count = p.attended_contests_count + 1,
              old_rating = p.old_rating + p.delta_rating
          FROM #$fullTableName p
          WHERE r.contest_title_slug = $contestTitleSlug
            AND p.contest_title_slug = $prevSlug
            AND r.data_region       = p.data_region
            AND r.user_slug         = p.user_slug
            AND p.delta_rating IS NOT NULL
        """
      case None =>
        // No biweekly to add, so do nothing
        DBIO.successful(0)

    val combined: DBIO[Int] =
      for
        fromUsers <- updateFromUsers
        filledDef <- fillDefaults
        withDelta <- updateWithPrevDelta
      yield fromUsers + filledDef + withDelta

    db.run(combined.transactionally)
  end fillUserOldRatingInfo

  def fillRankingUsingSubmissions(contestTitleSlug: String): Future[Int] =
    // Only users with submissions (score > 0) would have calculated ranking
    val action =
      sqlu"""
        WITH contest_info AS (
            SELECT
                c.start_time + c.duration_seconds * interval '1 second' AS contest_end_time
            FROM #${ContestRepository.fullTableName} c
            WHERE c.title_slug = $contestTitleSlug
        ),
        submission_aggregates AS (
            SELECT
                s.user_slug,
                s.data_region,
                SUM(q.credit) AS credit_sum,
                SUM(s.fail_count) AS fail_count_sum,
                MAX(s.time_point) AS max_submission_time
            FROM #${SubmissionRepository.fullTableName} s
            JOIN #${QuestionRepository.fullTableName} q ON q.id = s.question_id
            CROSS JOIN contest_info ci
            WHERE q.contest_title_slug = $contestTitleSlug
              AND s.time_point <= ci.contest_end_time
            GROUP BY s.user_slug, s.data_region
        ),
        ranked_users AS (
            SELECT
                sa.user_slug,
                sa.data_region,
                sa.credit_sum AS score,
                sa.max_submission_time AS finish_time,
                sa.max_submission_time + (sa.fail_count_sum * interval '5 minutes') AS penalty_time,
                RANK() OVER (
                    ORDER BY sa.credit_sum DESC,
                        sa.max_submission_time + (sa.fail_count_sum * interval '5 minutes') ASC
                ) AS rank
            FROM submission_aggregates sa
        )
        UPDATE #${RankingRepository.fullTableName} r
        SET rank = ru.rank
        FROM ranked_users ru
        WHERE r.contest_title_slug = $contestTitleSlug
          AND r.user_slug = ru.user_slug
          AND r.data_region = ru.data_region
      """
    db.run(action.transactionally)
  end fillRankingUsingSubmissions

  def updateRealTimeRanks(contestTitleSlug: String): Future[Int] =
    // Update the real_time_ranks using raw SQL
    val action =
      sqlu"""
        WITH contest_info AS (
          SELECT
            title_slug,
            start_time,
            duration_seconds / 60 AS duration_minutes
          FROM #${ContestRepository.fullTableName}
          WHERE title_slug = $contestTitleSlug
        ),
        all_participants AS (
          SELECT DISTINCT user_slug, data_region
          FROM #${RankingRepository.fullTableName}
          WHERE contest_title_slug = $contestTitleSlug AND score > 0
        ),
        submission_aggregates AS (
          SELECT
            s.user_slug,
            s.data_region,
            GREATEST(CEIL(EXTRACT(EPOCH FROM (s.time_point - ci.start_time)) / 60), 1)::int AS minute,
            s.time_point,
            q.credit,
            s.fail_count
          FROM #${SubmissionRepository.fullTableName} s
          JOIN #${QuestionRepository.fullTableName} q ON q.id = s.question_id
          CROSS JOIN contest_info ci
          WHERE q.contest_title_slug = ci.title_slug
            AND s.time_point <= ci.start_time + (ci.duration_minutes * interval '1 minute')
        ),
        user_states AS (
          SELECT
            m.minute,
            p.user_slug,
            p.data_region,
            COALESCE(SUM(sa.credit) FILTER (WHERE sa.minute <= m.minute), 0) AS score,
            COALESCE(
              MAX(sa.time_point) FILTER (WHERE sa.minute <= m.minute) +
              (SUM(sa.fail_count) FILTER (WHERE sa.minute <= m.minute) * interval '5 minutes'),
              (SELECT start_time FROM contest_info)
            ) AS penalty_time
          FROM all_participants p
          CROSS JOIN generate_series(1, (SELECT duration_minutes FROM contest_info)) m(minute)
          LEFT JOIN submission_aggregates sa
            ON sa.user_slug = p.user_slug
            AND sa.data_region = p.data_region
          GROUP BY m.minute, p.user_slug, p.data_region
        ),
        ranked_with_score AS (
          SELECT
            minute,
            user_slug,
            data_region,
            RANK() OVER (
              PARTITION BY minute
              ORDER BY score DESC, penalty_time ASC
            ) AS rank
          FROM user_states
          WHERE score > 0
        ),
        minute_stats AS (
          SELECT
            minute,
            COUNT(*) AS users_with_score
          FROM ranked_with_score
          GROUP BY minute
        ),
        ranked_data AS (
          -- Users with score > 0 get their actual rank
          SELECT
            minute,
            user_slug,
            data_region,
            rank
          FROM ranked_with_score

          UNION ALL

          -- ALL users with score = 0 get the SAME rank
          SELECT
            us.minute,
            us.user_slug,
            us.data_region,
            COALESCE(ms.users_with_score, 0) + 1 AS rank
          FROM user_states us
          LEFT JOIN minute_stats ms ON ms.minute = us.minute
          WHERE us.score = 0
        ),
        rank_arrays AS (
          SELECT
            user_slug,
            data_region,
            array_agg(rank ORDER BY minute)::integer[] AS real_time_ranks
          FROM ranked_data
          GROUP BY user_slug, data_region
        )
        UPDATE #${RankingRepository.fullTableName} r
        SET
          real_time_ranks = ra.real_time_ranks
        FROM rank_arrays ra
        WHERE r.contest_title_slug = $contestTitleSlug
          AND r.user_slug = ra.user_slug
          AND r.data_region = ra.data_region
      """

    db.run(action.transactionally)
  end updateRealTimeRanks

  def getRealTimeRanks(
    contestTitleSlug: String,
    dataRegion: String,
    userSlug: String,
  ): Future[Option[Seq[Int]]] =
    // Query the real_time_ranks using raw SQL
    val query =
      sql"""
        SELECT real_time_ranks
        FROM #${RankingRepository.fullTableName}
        WHERE contest_title_slug = $contestTitleSlug
          AND user_slug = $userSlug
          AND data_region = $dataRegion
      """.as[Seq[Int]].headOption
    db.run(query)
  end getRealTimeRanks

  def getRealTimeRanksByContest(
    contestTitleSlug: String
  ): Future[Seq[(
    dataRegion: String,
    userSlug: String,
    attendedContestsCount: Option[Int],
    oldRating: Option[Double],
    realTimeRanks: Seq[Int],
  )]] =
    given rankTupleGetResult: slick.jdbc.GetResult[(String, String, Seq[Int])] = r =>
      val dataRegion            = r.nextString()
      val userSlug              = r.nextString()
      val attendedContestsCount = intOptionGetResult(r)
      val oldRating             = doubleOptionGetResult(r)
      val realTimeRanks         = intSeqGetResult(r)
      (dataRegion, userSlug, realTimeRanks)
    val query =
      sql"""
        SELECT data_region, user_slug, attended_contests_count, old_rating, real_time_ranks
        FROM #${RankingRepository.fullTableName}
        WHERE contest_title_slug = $contestTitleSlug
          AND score > 0
      """.as[(String, String, Option[Int], Option[Double], Seq[Int])]
    db.run(query)
  end getRealTimeRanksByContest

  def updateRealTimeRatings(
    contestTitleSlug: String,
    userRealTimeRatings: Seq[(dataRegion: String, userSlug: String, realTimeRatings: Seq[Double])],
  ): Future[Int] =
    if userRealTimeRatings.isEmpty then
      return Future.successful(0)

    import upickle.default.{ Writer, write }
    case class RatingUpdate(region: String, slug: String, ratings: Seq[Double])
        derives Writer
    val updates: Seq[RatingUpdate] = userRealTimeRatings.map {
      case (dataRegion, userSlug, realTimeRatings) =>
        RatingUpdate(dataRegion, userSlug, realTimeRatings)
    }
    val json = write(updates)

    // Single SQL update using jsonb_to_recordset
    val action =
      sqlu"""
        WITH data AS (
          SELECT *
          FROM jsonb_to_recordset($json::jsonb)
            AS t(region varchar, slug varchar, ratings double precision[])
        )
        UPDATE #${RankingRepository.fullTableName} r
        SET real_time_ratings = d.ratings
        FROM data d
        WHERE r.user_slug = d.slug
          AND r.data_region = d.region
          AND r.contest_title_slug = $contestTitleSlug
      """
    db.run(action)
  end updateRealTimeRatings

  def getRealTimeRatings(
    contestTitleSlug: String,
    dataRegion: String,
    userSlug: String,
  ): Future[Option[Seq[Double]]] =
    // Query the real_time_ratings using raw SQL
    val query =
      sql"""
        SELECT real_time_ratings
        FROM #${RankingRepository.fullTableName}
        WHERE contest_title_slug = $contestTitleSlug
          AND user_slug = $userSlug
          AND data_region = $dataRegion
      """.as[Seq[Double]].headOption
    db.run(query)
  end getRealTimeRatings

end RankingRepository
