package core.contests.atcoder.repository

import postgres.{ Repository, Schema }


object AtCoderSchema extends Schema:

  val schemaName = "atcoder"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      ContestRepository
    )
