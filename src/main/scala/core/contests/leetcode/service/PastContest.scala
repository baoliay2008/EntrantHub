package core.contests.leetcode.service


import scala.concurrent.{ ExecutionContext, Future }

import core.contests.leetcode.model.{ Contest, ServerRegion }
import core.contests.leetcode.repository.ContestRepository
import core.contests.leetcode.repository.QuestionRealTimeCountRepository.upsertCountsByContest
import core.contests.leetcode.service.sourcing.RankingSubmissionSourcing.upsertRankingsAndSubmissionsLazily
import util.Extensions.getOrThrow
import util.FutureUtils.runSequentiallyAndDiscard
import util.Logging


class PastContest(
  val contest: Contest
)(
  using ec: ExecutionContext
) extends Logging:

  private val titleSlug = contest.titleSlug

  def upsertOldData(): Future[Unit] =
    for
      _ <- upsertRankingsAndSubmissionsLazily(
        titleSlug = titleSlug,
        serverRegion = ServerRegion.Us,
        rankingApiRegion = "global_v2",
      )
      _ <- upsertCountsByContest(contestTitleSlug = titleSlug)
    yield ()

end PastContest


object PastContest extends Logging:

  private given ec: ExecutionContext = ExecutionContext.global

  def loadBySlug(titleSlug: String): Future[PastContest] =
    for
      contestOpt <- ContestRepository.findBy(titleSlug)
      contest = contestOpt.getOrThrow(s"Contest $titleSlug not found in database")
    yield PastContest(contest)

  def upsertAllOldData(): Future[Unit] =
    for
      contestSlugs <- ContestRepository.findAllPastTitleSlugs()
      _ <- runSequentiallyAndDiscard(contestSlugs) { slug =>
        loadBySlug(slug)
          .flatMap(_.upsertOldData())
      }
    yield ()

end PastContest
