package core.contests.leetcode.service.sourcing


import java.time.Instant

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import core.contests.leetcode.model.ServerRegion.{ Cn, Us }
import core.contests.leetcode.model.{ Ranking, ServerRegion, Submission }
import core.contests.leetcode.repository.{ RankingRepository, SubmissionRepository }
import requests.HttpRequestManager.getRequest
import util.FutureUtils.{ runInBatchesAndCollect, runInBatchesAndDiscard }
import util.Logging


object RankingSubmissionSourcing extends Logging:

  private type RankingApiRegion = "local" | "global" | "global_v2"

  private val RankingApiPageSize = 25

  private given ec: ExecutionContext = ExecutionContext.global

  private def rankingApiUrl(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
    pageNum: Int = 1,
  ): String =
    val contestRankingUrl = serverRegion.contestRankingUrl(titleSlug)
    s"$contestRankingUrl?region=$rankingApiRegion&pagination=$pageNum"

  private def rankingApiMaxPageNum(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
  ): Future[Int] =
    val userNumFuture = requestContestUserNum(titleSlug, serverRegion, rankingApiRegion)
    userNumFuture.map {
      case Some(userNum) =>
        val maxPageNum = (userNum + RankingApiPageSize - 1) / RankingApiPageSize
        maxPageNum
      case None =>
        0
    }
  end rankingApiMaxPageNum

  private def requestContestUserNum(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
  ): Future[Option[Int]] =
    val url            = rankingApiUrl(titleSlug, serverRegion, rankingApiRegion)
    val responseFuture = getRequest(url)
    responseFuture.map { response =>
      val useNum = ujson.read(response).obj
        .get("user_num")
        .collect {
          case ujson.Num(n) => n.toInt
        }
      useNum
    }
  end requestContestUserNum

  /** Retrieves the number of users from the US and CN regions, respectively.
    *
    * @param titleSlug
    *   The contest title identifier.
    * @return
    *   A Future containing a tuple with user counts for the US and CN regions.
    */
  def requestContestUserNumLocal(titleSlug: String)
    : Future[(localUserNumUs: Option[Int], localUserNumCn: Option[Int])] =
    requestContestUserNum(titleSlug, Us, "local")
      .zip(requestContestUserNum(titleSlug, Cn, "local"))

  private def requestRankingsAndSubmissionsByPage(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
    pageNum: Int = 1,
  ): Future[(Seq[Ranking], Seq[Submission])] =
    def parseSubmissionsFromApiV1(jsonValue: ujson.Value): Seq[Submission] =
      val submissions = jsonValue("submissions").arr
        .zip(jsonValue("total_rank").arr) // badly designed V1 API, `user_slug` is in `total_rank`
        .flatMap { (submissionGroup, rankingJson) =>
          submissionGroup.obj.values.map { submissionJson =>
            Submission(
              id = submissionJson("submission_id").num.toLong,
              questionId = submissionJson("question_id").num.toInt,
              dataRegion = submissionJson("data_region").str,
              userSlug = rankingJson("user_slug").str.toLowerCase,
              timepoint = Instant.ofEpochSecond(submissionJson("date").num.toLong),
              failCount = submissionJson("fail_count").num.toInt,
              lang = submissionJson("lang").str,
            )
          }
        }.toSeq
      submissions
    end parseSubmissionsFromApiV1

    def parseSubmissionsFromApiV2(jsonValue: ujson.Value): Seq[Submission] =
      val submissions = jsonValue("total_rank").arr
        .flatMap { rankingJson =>
          rankingJson("submissions").obj.map { (questionId, submissionJson) =>
            Submission(
              id = submissionJson("submission_id").num.toLong,
              questionId = questionId.toInt,
              dataRegion = rankingJson("data_region").str,
              userSlug = rankingJson("user_slug").str.toLowerCase,
              timepoint = Instant.ofEpochSecond(submissionJson("date").num.toLong),
              failCount = submissionJson("fail_count").num.toInt,
              lang = submissionJson("lang").str,
            )
          }
        }.toSeq
      submissions
    end parseSubmissionsFromApiV2

    val url            = rankingApiUrl(titleSlug, serverRegion, rankingApiRegion, pageNum)
    val responseFuture = getRequest(url)
    // In the global_v2 API, ranks start from 0
    val rankOffset = if rankingApiRegion == "global_v2" then 1 else 0
    responseFuture.map { response =>
      val jsonValue = ujson.read(response)
      val rankings = jsonValue("total_rank").arr
        .map { rankingJson =>
          Ranking(
            contestTitleSlug = titleSlug,
            dataRegion = rankingJson("data_region").str,
            userSlug = rankingJson("user_slug").str.toLowerCase,
            rank = rankingJson("rank").num.toInt + rankOffset,
            score = rankingJson("score").num.toInt,
            finishTime = Instant.ofEpochSecond(rankingJson("finish_time").num.toLong),
          )
        }.toSeq
      val submissions = rankingApiRegion match
        case "global" | "local" => parseSubmissionsFromApiV1(jsonValue)
        case "global_v2"        => parseSubmissionsFromApiV2(jsonValue)
      (rankings, submissions)
    }
  end requestRankingsAndSubmissionsByPage

  def requestRankingsAndSubmissions(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
  ): Future[(Seq[Ranking], Seq[Submission])] =
    for
      maxPageNum <- rankingApiMaxPageNum(titleSlug, serverRegion, rankingApiRegion)
      pageResults <- runInBatchesAndCollect(1 to maxPageNum) { pageNum =>
        requestRankingsAndSubmissionsByPage(titleSlug, serverRegion, rankingApiRegion, pageNum)
      }
    yield
      val (rankingsPages, submissionsPages) = pageResults.unzip
      (rankingsPages.flatten, submissionsPages.flatten)
  end requestRankingsAndSubmissions

  def upsertRankingsAndSubmissions(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
  ): Future[(Seq[Ranking], Seq[Submission])] =
    for
      (rankings, submissions) <- requestRankingsAndSubmissions(
        titleSlug,
        serverRegion,
        rankingApiRegion,
      )
      rankingsCount    <- RankingRepository.bulkSyncByContest(titleSlug, rankings)
      submissionsCount <- SubmissionRepository.bulkSyncByContest(titleSlug, submissions)
      _ = info(s"upserted $rankingsCount rankings and $submissionsCount submissions")
    yield (rankings, submissions)
  end upsertRankingsAndSubmissions

  private def requestRankingsAndSubmissionsLazily(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
  ): Future[Iterator[Future[(Seq[Ranking], Seq[Submission])]]] =
    val maxPageNumFuture = rankingApiMaxPageNum(titleSlug, serverRegion, rankingApiRegion)
    maxPageNumFuture.map { maxPageNum =>
      (1 to maxPageNum).iterator.map { pageNum =>
        requestRankingsAndSubmissionsByPage(titleSlug, serverRegion, rankingApiRegion, pageNum)
      }
    }
  end requestRankingsAndSubmissionsLazily

  /** Fetches rankings and submissions for a given contest and region, and upserts them into the
    * database.
    *
    * This method lazily streams pages of `(Seq[Ranking], Seq[Submission])` pairs, processes them in
    * batches, and upserts the records concurrently into the database.
    *
    * ===Error Handling and Rationale===
    *
    * Instead of using `SubmissionRepository.upsert(submissions)` to batch upsert all submissions at
    * once, this implementation processes each `Ranking` and `Submission` individually with
    * per-record error handling.
    *
    * This design choice addresses a common failure mode during '''live''' contests, where
    * leaderboard data is still changing. In such cases, duplicate records may appear across pages
    * due to concurrent updates, leading to transient upsert conflicts (e.g., due to uniqueness
    * constraints or race conditions).
    *
    * Since this data is primarily for '''caching''' and not the final result, it is acceptable to
    * tolerate these failures during a contest. Errors for individual records are caught using
    * `.recover` and logged, while continuing with the rest of the batch.
    *
    * This strategy ensures that transient or partial failures do not compromise the processing of
    * the entire dataset.
    *
    * @param titleSlug
    *   The contest identifier (e.g., `"weekly-contest-400"`).
    * @param serverRegion
    *   The source region from which data is fetched (e.g., `US`, `CN`).
    * @param rankingApiRegion
    *   The external API region used for fetching ranking data.
    * @return
    *   A `Future[Unit]` that completes once all records have been processed.
    */
  def upsertRankingsAndSubmissionsLazily(
    titleSlug: String,
    serverRegion: ServerRegion,
    rankingApiRegion: RankingApiRegion,
  ): Future[Unit] =
    val onBatch: Future[(Seq[Ranking], Seq[Submission])] => Future[Unit] = pageFuture =>
      for
        (rankings, submissions) <- pageFuture
        rankingsCount <- runInBatchesAndCollect(rankings) { ranking =>
          RankingRepository.upsertOne(ranking).recover {
            case NonFatal(e) =>
              error(s"Failed to upsert ranking $ranking", e)
              0
          }
        }.map(_.sum)
        submissionsCount <- runInBatchesAndCollect(submissions) { submission =>
          SubmissionRepository.upsertOne(submission).recover {
            case NonFatal(e) =>
              error(s"Failed to upsert submission $submission", e)
              0
          }
        }.map(_.sum)
        _ = debug(
          s"upserted $rankingsCount rankings and $submissionsCount submissions"
        )
      yield ()
    for
      page <- requestRankingsAndSubmissionsLazily(titleSlug, serverRegion, rankingApiRegion)
      _    <- runInBatchesAndDiscard(page)(onBatch)
    yield ()
  end upsertRankingsAndSubmissionsLazily

end RankingSubmissionSourcing
