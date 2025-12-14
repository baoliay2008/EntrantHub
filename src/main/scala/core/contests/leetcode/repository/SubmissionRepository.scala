package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.Submission
import core.contests.leetcode.model.Submission.SubmissionId
import postgres.StageableRepository


private class SubmissionTable(tag: Tag, tableName: String = "submissions")
    extends Table[Submission](tag, Some(LeetCodeSchema.schemaName), tableName):

  def id         = column[Long]("id", O.PrimaryKey)
  def questionId = column[Int]("question_id")
  def dataRegion = column[String]("data_region")
  def userSlug   = column[String]("user_slug")
  def timepoint  = column[Instant]("time_point")
  def failCount  = column[Int]("fail_count")
  def lang       = column[String]("lang")
  def updatedAt  = column[Instant]("updated_at")

  def idxQuestionUser = index("submissions_question_id_user_slug_idx", (questionId, userSlug))
  def idxUserSlug     = index("submissions_user_slug_idx", userSlug)
  def idxDate         = index("submissions_time_point_idx", timepoint)

  def * = (
    id,
    questionId,
    dataRegion,
    userSlug,
    timepoint,
    failCount,
    lang,
    updatedAt,
  ).mapTo[Submission]

end SubmissionTable


object SubmissionRepository extends StageableRepository[Submission, SubmissionId, SubmissionTable]:

  protected def customTableQuery(name: String): TableQuery[SubmissionTable] =
    TableQuery[SubmissionTable](tag => SubmissionTable(tag, name))

  protected val tableQuery = TableQuery[SubmissionTable]

  protected def idMatcher(id: SubmissionId): SubmissionTable => Rep[Boolean] =
    _.id === id

  def bulkSyncByContest(
    contestTitleSlug: String,
    submissions: Seq[Submission],
  ): Future[Int] =
    QuestionRepository.exists(_.contestTitleSlug === contestTitleSlug).flatMap {
      case true =>
        val questionIdsSubquery = QuestionRepository.tableQuery
          .filter(_.contestTitleSlug === contestTitleSlug)
          .map(_.id)
        bulkSync(submissions, _.questionId in questionIdsSubquery)
      case false =>
        throw RuntimeException(s"There is no question data for $contestTitleSlug in the database.")
    }
  end bulkSyncByContest

end SubmissionRepository
