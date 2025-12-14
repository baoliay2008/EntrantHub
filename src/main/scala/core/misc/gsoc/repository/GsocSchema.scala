package core.misc.gsoc.repository

import postgres.{ Repository, Schema }


object GsocSchema extends Schema:

  val schemaName = "gsoc"

  protected val repositories: List[Repository[?, ?, ?]] =
    List(
      OrganizationRepository,
      CategoryRepository,
      OrganizationCategoriesRepository,
      ProjectRepository,
      MilestoneRepository,
    )

end GsocSchema
