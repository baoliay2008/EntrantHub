package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.Question
import core.contests.leetcode.model.Question.QuestionId
import postgres.Repository


private class QuestionTable(tag: Tag)
    extends Table[Question](tag, Some(LeetCodeSchema.schemaName), "questions"):

  def contestTitleSlug = column[String]("contest_title_slug")
  def id               = column[Int]("id", O.PrimaryKey)
  def idUs             = column[Int]("id_us")
  def idCn             = column[Int]("id_cn")
  def titleSlug        = column[String]("title_slug")
  def titleUs          = column[String]("title_us")
  def titleCn          = column[String]("title_cn")
  def difficulty       = column[Int]("difficulty")
  def credit           = column[Int]("credit")
  def updatedAt        = column[Instant]("updated_at")

  def idxContestTitleSlug  = index("questions_contest_title_slug_idx", contestTitleSlug)
  def idxQuestionTitleSlug = index("questions_title_slug_key", titleSlug, unique = true)

  def * = (
    contestTitleSlug,
    id,
    idUs,
    idCn,
    titleSlug,
    titleUs,
    titleCn,
    difficulty,
    credit,
    updatedAt,
  ).mapTo[Question]

end QuestionTable


object QuestionRepository extends Repository[Question, QuestionId, QuestionTable]:

  val tableQuery = TableQuery[QuestionTable]

  protected def idMatcher(id: QuestionId): QuestionTable => Rep[Boolean] =
    _.id === id

  def findByContest(contestTitleSlug: String): Future[Seq[Question]] =
    find(where = Some(_.contestTitleSlug === contestTitleSlug))

end QuestionRepository
