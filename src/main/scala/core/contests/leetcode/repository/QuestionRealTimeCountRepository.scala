package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.QuestionRealTimeCount
import core.contests.leetcode.model.QuestionRealTimeCount.QuestionRealTimeCountId
import postgres.Repository


class QuestionRealTimeCountsTable(tag: Tag)
    extends Table[QuestionRealTimeCount](
      tag,
      Some(LeetCodeSchema.schemaName),
      "question_real_time_counts",
    ):

  def questionId = column[Int]("question_id")
  def timepoint  = column[Instant]("time_point")
  def lang       = column[String]("lang")
  def count      = column[Int]("count")

  def pk = primaryKey("question_real_time_counts_pkey", (questionId, timepoint, lang))

  def * = (
    questionId,
    timepoint,
    lang,
    count,
  ).mapTo[QuestionRealTimeCount]

end QuestionRealTimeCountsTable


object QuestionRealTimeCountRepository
    extends Repository[QuestionRealTimeCount, QuestionRealTimeCountId, QuestionRealTimeCountsTable]:

  protected val tableQuery = TableQuery[QuestionRealTimeCountsTable]

  protected def idMatcher(id: QuestionRealTimeCountId)
    : QuestionRealTimeCountsTable => Rep[Boolean] =
    t =>
      t.questionId === id.questionId &&
        t.timepoint === id.timepoint &&
        t.lang === id.lang

  def upsertCountsByContest(contestTitleSlug: String): Future[Int] =
    val action =
      sqlu"""
        WITH RECURSIVE contest_info AS (
            SELECT
                c.start_time AS start_ts,
                c.start_time + c.duration_seconds * interval '1 second' AS end_ts,
                c.duration_seconds / 60 AS duration_minutes
            FROM #${ContestRepository.fullTableName} c
            WHERE c.title_slug = $contestTitleSlug
        ),
        -- Pre-aggregate all submissions by minute
        minute_submissions AS (
            SELECT
                s.question_id,
                s.lang,
                date_trunc('minute', s.time_point) + interval '1 minute' AS minute,
                COUNT(*) AS count
            FROM #${SubmissionRepository.fullTableName} s
            JOIN #${QuestionRepository.fullTableName} q ON q.id = s.question_id
            CROSS JOIN contest_info ci
            WHERE q.contest_title_slug = $contestTitleSlug
              AND s.time_point <= ci.end_ts
            GROUP BY s.question_id, s.lang, date_trunc('minute', s.time_point)
        ),
        -- Get all questions and languages we need to track
        base_dimensions AS (
            SELECT DISTINCT
                q.id AS question_id,
                l.lang
            FROM #${QuestionRepository.fullTableName} q
            CROSS JOIN (
                SELECT DISTINCT lang FROM minute_submissions
                UNION ALL
                SELECT 'ALL'
            ) l
            WHERE q.contest_title_slug = $contestTitleSlug
        ),
        -- Build cumulative counts minute by minute
        cumulative_build AS (
            -- Base case: first minute
            SELECT
                bd.question_id,
                ci.start_ts + interval '1 minute' AS time_point,
                bd.lang,
                COALESCE(ms.count, 0) AS cumulative_count,
                1 AS minute_num
            FROM base_dimensions bd
            CROSS JOIN contest_info ci
            LEFT JOIN minute_submissions ms
                ON ms.question_id = bd.question_id
                AND ms.lang = bd.lang
                AND ms.minute = ci.start_ts + interval '1 minute'

            UNION ALL

            -- Recursive case: add next minute's counts
            SELECT
                cb.question_id,
                cb.time_point + interval '1 minute' AS time_point,
                cb.lang,
                cb.cumulative_count + COALESCE(ms.count, 0) AS cumulative_count,
                cb.minute_num + 1 AS minute_num
            FROM cumulative_build cb
            CROSS JOIN contest_info ci
            LEFT JOIN minute_submissions ms
                ON ms.question_id = cb.question_id
                AND ms.lang = cb.lang
                AND ms.minute = cb.time_point + interval '1 minute'
            WHERE cb.minute_num < ci.duration_minutes
        ),
        -- Calculate ALL language totals
        lang_specific_totals AS (
            SELECT * FROM cumulative_build
            WHERE lang != 'ALL'
        ),
        all_lang_totals AS (
            SELECT
                question_id,
                time_point,
                'ALL' AS lang,
                SUM(cumulative_count) AS cumulative_count
            FROM lang_specific_totals
            GROUP BY question_id, time_point
        ),
        -- Combine language-specific and ALL counts
        final_counts AS (
            SELECT
                question_id,
                time_point,
                lang,
                cumulative_count
            FROM lang_specific_totals

            UNION ALL

            SELECT
                question_id,
                time_point,
                lang,
                cumulative_count
            FROM all_lang_totals
        )
        -- Final insert
        INSERT INTO #$fullTableName (question_id, time_point, lang, count)
        SELECT
            question_id,
            time_point,
            lang,
            cumulative_count AS count
        FROM final_counts
        ON CONFLICT (question_id, time_point, lang)
        DO UPDATE SET count = EXCLUDED.count;
      """
    // transactionally is not strictly necessary here, but it doesn't hurt :)
    db.run(action.transactionally)
  end upsertCountsByContest

  def getCountsByContest(
    contestTitleSlug: String
  ): Future[Seq[QuestionRealTimeCount]] =
    val questionIds = QuestionRepository.tableQuery
      .filter(_.contestTitleSlug === contestTitleSlug)
      .map(_.id)
    val query = tableQuery
      .filter(_.questionId in questionIds)
      .sortBy(c => (c.questionId.asc, c.timepoint.asc, c.lang.asc))
    db.run(query.result)

end QuestionRealTimeCountRepository
