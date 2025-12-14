package core.hackathons.devpost.repository

import postgres.{ Repository, Schema }


object DevPostSchema extends Schema:

  val schemaName = "devpost"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      HackathonRepository,
      CategoryRepository,
      HackathonCategoryLinkRepository,
    )

end DevPostSchema
