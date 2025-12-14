package core.contests.leetcode.service


import java.time.{ Duration, Instant }

import scala.concurrent.{ ExecutionContext, Future }

import core.contests.leetcode.model.ServerRegion.{ Cn, Us }
import core.contests.leetcode.model.User.UserId
import core.contests.leetcode.model.{ Contest, ContestType, ServerRegion }
import core.contests.leetcode.repository.QuestionRealTimeCountRepository.upsertCountsByContest
import core.contests.leetcode.repository.{ ContestRepository, RankingRepository, UserRepository }
import core.contests.leetcode.service.rating.EloRatingFft
import core.contests.leetcode.service.sourcing.RankingSubmissionSourcing.{
  upsertRankingsAndSubmissions,
  upsertRankingsAndSubmissionsLazily,
}
import core.contests.leetcode.service.sourcing.UserSourcing.requestUser
import util.Extensions.getOrThrow
import util.FutureUtils.runInBatchesAndDiscard
import util.Logging


class OngoingContest(
  val contest: Contest
)(
  using ec: ExecutionContext
) extends Logging:

  private val titleSlug = contest.titleSlug

  private def prefetchLocalRankingsAndSubmissions(): Future[Unit] =
    val prefetchSingleLocalRegion: ServerRegion => Future[Unit] = region =>
      upsertRankingsAndSubmissionsLazily(
        titleSlug = titleSlug,
        serverRegion = region,
        rankingApiRegion = "local",
      )
    info(s"prefetch contest $titleSlug started")
    val regions = List(Us, Cn)
    for
      _ <- Future.traverse(regions)(prefetchSingleLocalRegion)
      _ = info(s"prefetch contest $titleSlug finished")
    yield ()
  end prefetchLocalRankingsAndSubmissions

  private def syncUserForcefully(userId: UserId): Future[Unit] =
    syncUser(userId, None)

  private def syncUser(
    userId: UserId,
    cutoffTime: Option[Instant],
  ): Future[Unit] =
    for
      isFresh <- cutoffTime match
        case Some(t) =>
          UserRepository.isRecentlyUpdated(userId = userId, since = t)
        case None =>
          Future.successful(false) // always treat as stale
      _ <- if isFresh then Future.unit
      else
        requestUser(userId).flatMap {
          case Some(user) => UserRepository.upsertOne(user)
          case None =>
            warn(s"$titleSlug user $userId cannot be requested, possibly deleted")
            Future.unit
        }
    yield ()
  end syncUser

  private def syncUsers(
    positiveScoreOnly: Boolean
  ): Future[Unit] =
    val hoursBefore = contest.contestType.getOrThrow("ContestType is None") match
      case ContestType.Biweekly => 12
      case ContestType.Weekly   => 24
    val cutoffTime = contest.startTime.minus(Duration.ofHours(hoursBefore))
    info(s"$titleSlug sync users since cutoffTime $cutoffTime")
    val syncUserWithCutoffTime: UserId => Future[Unit] =
      syncUser(_, Some(cutoffTime))
    for
      userIds <- RankingRepository.findUserIdsByContest(titleSlug, positiveScoreOnly)
      (cnUserIds, usUserIds) = userIds.partition(_.dataRegion == "CN")
      _ = info(
        s"$titleSlug cnUserKeys.length=${cnUserIds.length} usUserKeys.length=${usUserIds.length}"
      )
      cnPrefetch = runInBatchesAndDiscard(cnUserIds.iterator)(syncUserWithCutoffTime)
      usPrefetch = runInBatchesAndDiscard(usUserIds.iterator)(syncUserWithCutoffTime)
      _ <- Future.sequence(List(cnPrefetch, usPrefetch))
    yield ()
  end syncUsers

  private def prefetch(): Future[Unit] =
    for
      _ <- prefetchLocalRankingsAndSubmissions()
      _ <- syncUsers(positiveScoreOnly = true)
    yield ()

  private def predictRating(): Future[Unit] =
    for
      filledRows <- RankingRepository.fillUserOldRatingInfo(titleSlug)
      _ = info(s"$titleSlug filled $filledRows rows with user old rating info")
      rankings <- RankingRepository.findByContest(titleSlug)
      ranks = rankings
        .map(_.rank)
        .toArray
      oldRatings = rankings
        .map(_.oldRating.getOrThrow("Missing old rating"))
        .toArray
      attendedContestsCounts = rankings
        .map(_.attendedContestsCount.getOrThrow("Missing attendedContestsCount"))
        .toArray
      (expectedRatings, deltas) = EloRatingFft.ratingAdjustments(
        ranks = ranks,
        ratings = oldRatings,
        attendedContestsCounts = attendedContestsCounts,
      )
      updatedRankings = rankings
        .indices
        .map { i =>
          val ranking        = rankings(i)
          val delta          = deltas(i)
          val expectedRating = expectedRatings(i)
          ranking.copy(
            expectedRating = Some(expectedRating),
            deltaRating = Some(delta),
          )
        }
      _ <- RankingRepository.bulkSyncByContest(titleSlug, updatedRankings)
    yield ()

  private def updateRealTimeRanksAndRatings(): Future[Unit] =
    for
      updatedRanksCount <- RankingRepository.updateRealTimeRanks(titleSlug)
      _ = info(s"$titleSlug updated $updatedRanksCount rows with realtime ranks")
      rankingRows <- RankingRepository.getRealTimeRanksByContest(titleSlug)

      dataRegions = rankingRows.map(_.dataRegion).toArray
      userSlugs   = rankingRows.map(_.userSlug).toArray
      oldRatings = rankingRows
        .map(_.oldRating.getOrThrow("Missing old rating"))
        .toArray
      attendedContestsCounts = rankingRows
        .map(_.attendedContestsCount.getOrThrow("Missing attendedContestsCount"))
        .toArray
      realTimeRanksMatrix = rankingRows.map(_.realTimeRanks.toArray).toArray

      realTimeRatingsMatrix = EloRatingFft.computeRealTimeRatingsMatrix(
        ratings = oldRatings,
        attendedContestsCounts = attendedContestsCounts,
        realTimeRanksMatrix = realTimeRanksMatrix,
      )

      userRealTimeRatings = dataRegions.indices.map { i =>
        (dataRegions(i), userSlugs(i), realTimeRatingsMatrix(i).toSeq)
      }

      updatedRatingsCount <- RankingRepository.updateRealTimeRatings(
        contestTitleSlug = titleSlug,
        userRealTimeRatings = userRealTimeRatings,
      )
      _ = info(s"$titleSlug updated $updatedRatingsCount rows with realtime ratings")
    yield ()
  end updateRealTimeRanksAndRatings

  private def predict(): Future[Unit] =
    for
      _ <- upsertRankingsAndSubmissions(
        titleSlug = titleSlug,
        serverRegion = Us,
        rankingApiRegion = "global_v2",
      )
      _ <- syncUsers(positiveScoreOnly = true)
      _ <- predictRating()
      _ <- updateRealTimeRanksAndRatings()
      _ <- upsertCountsByContest(contestTitleSlug = titleSlug)
    yield ()

end OngoingContest


object OngoingContest extends Logging:
  private given ec: ExecutionContext = ExecutionContext.global

  def loadBySlug(titleSlug: String): Future[OngoingContest] =
    for
      contestOpt <- ContestRepository.findBy(titleSlug)
      contest = contestOpt.getOrThrow(s"Contest $titleSlug not found in database")
    yield OngoingContest(contest)

  private def weeksSince(since: Instant): Long =
    val duration: Duration = Duration.between(since, Instant.now())
    duration.toDays / 7

  private def currentWeeklyContestSlug(): String =
    val BaseIndex  = 294
    val BaseTime   = Instant.parse("2022-05-22T02:30:00Z")
    val deltaWeeks = weeksSince(BaseTime)
    s"weekly-contest-${BaseIndex + deltaWeeks}"

  private def currentBiweeklyContestSlug(): Option[String] =
    val BaseIndex  = 78
    val BaseTime   = Instant.parse("2022-05-14T14:30:00Z")
    val deltaWeeks = weeksSince(BaseTime)
    Option.when(deltaWeeks % 2 == 0) {
      val contestNum = BaseIndex + (deltaWeeks / 2)
      s"biweekly-contest-$contestNum"
    }

  def prefetchBySlug(titleSlug: String): Future[Unit] =
    loadBySlug(titleSlug)
      .flatMap(_.prefetch())

  def predictBySlug(titleSlug: String): Future[Unit] =
    loadBySlug(titleSlug)
      .flatMap(_.predict())

  private def runCurrent(
    contestType: ContestType,
    actionName: String,
    action: String => Future[Unit],
  ): Future[Unit] =
    val slugOpt = contestType match
      case ContestType.Weekly   => Some(currentWeeklyContestSlug())
      case ContestType.Biweekly => currentBiweeklyContestSlug()

    slugOpt.fold {
      info(s"No $contestType contest this week, skipping $actionName")
      Future.unit
    } { slug =>
      info(s"Starting $actionName for $slug")
      action(slug)
    }
  end runCurrent

  def prefetchCurrent(contestType: ContestType): Future[Unit] =
    runCurrent(contestType, "prefetch", prefetchBySlug)

  def predictCurrent(contestType: ContestType): Future[Unit] =
    runCurrent(contestType, "predict", predictBySlug)

  def predictCurrentBoth(): Future[Unit] =
    for
      _ <- predictCurrent(ContestType.Biweekly)
      _ <- predictCurrent(ContestType.Weekly)
    yield ()

end OngoingContest
