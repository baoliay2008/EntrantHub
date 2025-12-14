package core.contests.leetcode.service.sourcing


import java.time.Instant

import scala.concurrent.{ ExecutionContext, Future }

import core.contests.leetcode.model.ServerRegion.{ Cn, Us }
import core.contests.leetcode.model.{ Contest, Question, ServerRegion }
import core.contests.leetcode.repository.{ ContestRepository, QuestionRepository }
import requests.HttpRequestManager.{ getRequest, postJsonRequest }
import util.Extensions.getOrThrow
import util.FutureUtils.runSequentiallyAndDiscard
import util.Logging


object ContestQuestionSourcing extends Logging:
  private given ec: ExecutionContext = ExecutionContext.global

  private def requestUpcomingContestTitleSlugs(): Future[Seq[String]] =
    def requestBuildId(): Future[String] =
      val contestPageResponse = getRequest(Us.contestHomepageUrl)
      contestPageResponse.map { response =>
        val buildIdPattern = """"buildId":\s*"(.*?)",""".r
        val buildId = buildIdPattern
          .findFirstMatchIn(response)
          .map(_.group(1))
          .getOrThrow("Cannot find buildId.")
        buildId
      }
    end requestBuildId

    def requestNextData(buildId: String): Future[Seq[String]] =
      val nextContestsResponse = getRequest(s"${Us.baseUrl}/_next/data/$buildId/contest.json")
      nextContestsResponse.map { response =>
        val topTwoContestsPattern = """"topTwoContests":(\[.*?])""".r
        val topTwoContestsString = topTwoContestsPattern
          .findFirstMatchIn(response)
          .map(_.group(1))
          .getOrThrow("Cannot find topTwoContests.")
        val titleSlugPattern = """"titleSlug"\s*:\s*"([^"]+)"""".r
        val titleSlugs = titleSlugPattern
          .findAllMatchIn(topTwoContestsString)
          .map(_.group(1))
          .toSeq
        titleSlugs
      }
    end requestNextData

    for
      buildId                 <- requestBuildId()
      topTwoContestTitleSlugs <- requestNextData(buildId)
    yield topTwoContestTitleSlugs
  end requestUpcomingContestTitleSlugs

  private def requestPastContestTitleSlugsByPage(pageNum: Int = 1): Future[Seq[String]] =
    val url = Us.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query pastContests($pageNo: Int) {
          |  pastContests(pageNo: $pageNo) {
          |    data { title titleSlug startTime duration }
          |  }
          |}""".stripMargin,
      "variables" -> Map("pageNo" -> pageNum),
    )
    val responseFuture = postJsonRequest(url, jsonBody = jsonBody)
    responseFuture.map { response =>
      ujson.read(response)("data")("pastContests")("data")
        .arr
        .map(contestJson => contestJson("titleSlug").str)
        .toSeq
    }
  end requestPastContestTitleSlugsByPage

  private def requestContestDetailRaw(
    titleSlug: String,
    serverRegion: ServerRegion,
  ): Future[String] =
    val url = serverRegion.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query contestDetailPage($contestSlug: String!) {
          |  contestDetailPage(contestSlug: $contestSlug) {
          |    startTime
          |    duration
          |    titleSlug
          |    title
          |    discussUrl
          |    registerUserNum
          |  }
          |}""".stripMargin,
      "variables"     -> Map("contestSlug" -> titleSlug),
      "operationName" -> "contestDetailPage",
    )
    postJsonRequest(url, jsonBody = jsonBody)
  end requestContestDetailRaw

  private def requestContestInfoRaw(
    titleSlug: String,
    serverRegion: ServerRegion,
  ): Future[String] =
    getRequest(serverRegion.contestInfoUrl(titleSlug))

  private def requestContestAndQuestions(
    titleSlug: String
  ): Future[(Contest, Seq[Question])] =
    val usInfoRawFuture    = requestContestInfoRaw(titleSlug, Us)
    val cnInfoRawFuture    = requestContestInfoRaw(titleSlug, Cn)
    val usDetailRawFuture  = requestContestDetailRaw(titleSlug, Us)
    val cnDetailRawFuture  = requestContestDetailRaw(titleSlug, Cn)
    val localUserNumFuture = RankingSubmissionSourcing.requestContestUserNumLocal(titleSlug)

    for
      usInfoRaw    <- usInfoRawFuture
      cnInfoRaw    <- cnInfoRawFuture
      usDetailRaw  <- usDetailRawFuture
      cnDetailRaw  <- cnDetailRawFuture
      localUserNum <- localUserNumFuture

      usInfoJson   = ujson.read(usInfoRaw)
      cnInfoJson   = ujson.read(cnInfoRaw)
      usDetailJson = ujson.read(usDetailRaw)("data")("contestDetailPage")
      cnDetailJson = ujson.read(cnDetailRaw)("data")("contestDetailPage")

      contest = Contest(
        titleSlug = titleSlug,
        startTime = Instant.ofEpochSecond(usDetailJson("startTime").num.toLong),
        durationSeconds = usDetailJson("duration").num.toInt,
        titleUs = usDetailJson("title").str,
        titleCn = cnDetailJson("title").str,
        unratedUs = usInfoJson("unrated").bool,
        unratedCn = cnInfoJson("unrated").bool,
        rankingUpdatedUs = usInfoJson("ranking_updated").bool,
        rankingUpdatedCn = cnInfoJson("ranking_updated").bool,
        registerUserNumUs = usDetailJson("registerUserNum").num.toInt,
        registerUserNumCn = cnDetailJson("registerUserNum").num.toInt,
        userNumUs = localUserNum.localUserNumUs,
        userNumCn = localUserNum.localUserNumCn, // Some(cnInfoJson("user_num").num.toInt),
        discussUrlUs = usDetailJson("discussUrl").strOpt,
        discussUrlCn = cnDetailJson("discussUrl").strOpt,
      )

      usQuestionsJsonArr = usInfoJson("questions").arr
      cnQuestionsJsonArr = cnInfoJson("questions").arr
      questions = usQuestionsJsonArr.zip(cnQuestionsJsonArr)
        .map {
          (usQuestionJson, cnQuestionJson) =>
            Question(
              contestTitleSlug = titleSlug,
              id = usQuestionJson("question_id").num.toInt,
              idUs = usQuestionJson("id").num.toInt,
              idCn = cnQuestionJson("id").num.toInt,
              titleSlug = usQuestionJson("title_slug").str,
              titleUs = usQuestionJson("title").str,
              titleCn = cnQuestionJson("title").str,
              difficulty = usQuestionJson("difficulty").num.toInt,
              credit = usQuestionJson("credit").num.toInt,
            )
        }.toSeq
    yield (contest, questions)
    end for
  end requestContestAndQuestions

  private def requestContestAndQuestions(
    titleSlugs: Seq[String]
  ): Future[(Seq[Contest], Seq[Question])] =
    for
      contestsAndQuestions <- Future.traverse(titleSlugs)(requestContestAndQuestions)
      (contests, nestedQuestions) = contestsAndQuestions.unzip
      questions                   = nestedQuestions.flatten
    yield (contests, questions)

  private def requestUpcomingContests(): Future[Seq[Contest]] =
    for
      slugs                 <- requestUpcomingContestTitleSlugs()
      (contests, questions) <- requestContestAndQuestions(slugs)
    // questions must be empty here for the upcoming contests because they are not published yet
    yield contests

  private def requestPastContestsAndQuestionsByPage(
    pageNum: Int = 1
  ): Future[(Seq[Contest], Seq[Question])] =
    for
      slugs <- requestPastContestTitleSlugsByPage(pageNum)
      res   <- requestContestAndQuestions(slugs)
      _ = info(s"requested past contest page=$pageNum")
    yield res

  private def contestHomepageMaxPageNum(): Future[Int] =
    val contestPageResponse = getRequest(Us.contestHomepageUrl)
    contestPageResponse.map { response =>
      val maxPageNumPattern = """"pageNum":\s*(\d+)""".r
      val maxPageNum = maxPageNumPattern
        .findFirstMatchIn(response)
        .map(_.group(1))
        .getOrThrow("Cannot find maxPageNum.")
        .toInt
      maxPageNum
    }
  end contestHomepageMaxPageNum

  private def requestPastContestsAndQuestionsLazily()
    : Future[Iterator[Future[(Seq[Contest], Seq[Question])]]] =
    val maxPageNumFuture = contestHomepageMaxPageNum()
    maxPageNumFuture.map { maxPageNum =>
      (1 to maxPageNum).iterator.map { pageNum =>
        requestPastContestsAndQuestionsByPage(pageNum)
      }
    }
  end requestPastContestsAndQuestionsLazily

  def upsertUpcomingContests(): Future[Unit] =
    for
      sourcedContests <- requestUpcomingContests()
      _ = info(
        s"requested upcoming contests ${sourcedContests.map(_.titleSlug).mkString("[", ", ", "]")}"
      )
      contests <- ContestRepository.upsertFromSourcing(sourcedContests)
      _ = info(s"upserted ${contests.length} contests")
    yield ()
  end upsertUpcomingContests

  // Reusable batch processor for contest data
  private val processContestDataBatch: Future[(Seq[Contest], Seq[Question])] => Future[Unit] =
    pageFuture =>
      for
        (sourcedContests, questions) <- pageFuture
        contests                     <- ContestRepository.upsertFromSourcing(sourcedContests)
        questionCount                <- QuestionRepository.upsert(questions)
        _ = info(s"upserted ${contests.length} contests and $questionCount questions")
      yield ()

  def upsertRecentPastContestsAndQuestions(): Future[Unit] =
    processContestDataBatch(requestPastContestsAndQuestionsByPage(1))

  def upsertRecentContestsAndQuestions(): Future[Unit] =
    for
      _ <- upsertUpcomingContests()
      _ <- upsertRecentPastContestsAndQuestions()
    yield ()

  def upsertAllPastContestsAndQuestions(): Future[Unit] =
    requestPastContestsAndQuestionsLazily().flatMap { pages =>
      runSequentiallyAndDiscard(pages)(processContestDataBatch)
    }

end ContestQuestionSourcing
