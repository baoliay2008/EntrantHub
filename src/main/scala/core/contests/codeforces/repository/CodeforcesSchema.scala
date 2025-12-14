package core.contests.codeforces.repository

import postgres.{ Repository, Schema }


object CodeforcesSchema extends Schema:

  val schemaName = "codeforces"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      ContestRepository
    )
