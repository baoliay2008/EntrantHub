package postgres


import scala.concurrent.{ ExecutionContext, Future }

import util.FutureUtils.runSequentiallyAndDiscard


trait Schema:

  def schemaName: String

  protected def repositories: List[Repository[?, ?, ?]]

  final def initSchemas()(
    using ec: ExecutionContext
  ): Future[Unit] =
    runSequentiallyAndDiscard(repositories)(_.initSchema())

end Schema
