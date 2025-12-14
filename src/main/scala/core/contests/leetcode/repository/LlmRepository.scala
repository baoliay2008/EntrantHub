package core.contests.leetcode.repository


import java.time.Instant

import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api.*

import core.contests.leetcode.model.Llm
import core.contests.leetcode.model.Llm.LlmId
import postgres.Repository


private class LlmTable(tag: Tag)
    extends Table[Llm](tag, Some(LeetCodeSchema.schemaName), "llms"):

  def id          = column[Int]("id", O.PrimaryKey)
  def name        = column[String]("name")
  def logoUrl     = column[String]("logo_url")
  def companyName = column[String]("company_name")
  def info        = column[String]("info")
  def updatedAt   = column[Instant]("updated_at")

  def * = (
    id,
    name,
    logoUrl,
    companyName,
    info,
    updatedAt,
  ).mapTo[Llm]

end LlmTable


object LlmRepository extends Repository[Llm, LlmId, LlmTable]:

  protected val tableQuery = TableQuery[LlmTable]

  protected def idMatcher(id: LlmId): LlmTable => Rep[Boolean] =
    llmTable => llmTable.id === id

end LlmRepository
