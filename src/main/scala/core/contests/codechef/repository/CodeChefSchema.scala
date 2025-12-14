package core.contests.codechef.repository

import postgres.{ Repository, Schema }


object CodeChefSchema extends Schema:

  val schemaName = "codechef"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      ContestRepository
    )
