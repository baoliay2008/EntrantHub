package core.contests.leetcode.service.sourcing


import scala.concurrent.{ ExecutionContext, Future }

import core.contests.leetcode.model.{ Llm, LlmContestRanking, ServerRegion }
import core.contests.leetcode.repository.{
  ContestRepository,
  LlmContestRankingRepository,
  LlmRepository,
}
import requests.HttpRequestManager.postJsonRequest
import util.FutureUtils.{ runInBatchesAndCollect, runInBatchesAndDiscard }
import util.Logging


object LlmSourcing extends Logging:
  private given ec: ExecutionContext = ExecutionContext.global

  private def requestContestLlmRankingRaw(
    contestSlug: String,
    serverRegion: ServerRegion,
  ): Future[String] =
    val url = serverRegion.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query contestLlmRanking($contestSlug: String!) {
          |  contestLlmRanking(contestSlug: $contestSlug) {
          |    aiModel {
          |      companyName
          |      logoUrl
          |      name
          |      info
          |      id
          |    }
          |    avgScore
          |    maxScore
          |  }
          |}""".stripMargin,
      "variables"     -> Map("contestSlug" -> contestSlug),
      "operationName" -> "contestLlmRanking",
    )
    postJsonRequest(url, jsonBody = jsonBody)
  end requestContestLlmRankingRaw

  private def requestContestLlmDetailRaw(
    aiModelId: String,
    contestSlug: String,
    serverRegion: ServerRegion,
  ): Future[String] =
    val url = serverRegion.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query contestLlmDetail($aiModelId: ID!, $contestSlug: String!) {
          |  contestLlmDetail(aiModelId: $aiModelId, contestSlug: $contestSlug) {
          |    acRate
          |    avgTriedTimes
          |    questionScores
          |    detailedStats
          |  }
          |}""".stripMargin,
      "variables" -> ujson.Obj(
        "contestSlug" -> contestSlug,
        "aiModelId"   -> aiModelId,
      ),
      "operationName" -> "contestLlmDetail",
    )
    postJsonRequest(url, jsonBody = jsonBody)
  end requestContestLlmDetailRaw

  private def requestContestLlmRanking(
    contestSlug: String,
    serverRegion: ServerRegion,
  ): Future[Seq[(llm: Llm, avgScore: Double, maxScore: Double)]] =
    requestContestLlmRankingRaw(contestSlug, serverRegion).map { response =>
      val json = ujson.read(response)
      val rankings =
        if json("data")("contestLlmRanking").isNull
        then Seq.empty
        else json("data")("contestLlmRanking").arr

      rankings.map { ranking =>
        val aiModel = ranking("aiModel")
        val llm = Llm(
          id = aiModel("id").str.toInt,
          name = aiModel("name").str,
          logoUrl = aiModel("logoUrl").str,
          companyName = aiModel("companyName").str,
          info = aiModel("info").str,
        )
        val avgScore = ranking("avgScore").num
        val maxScore = ranking("maxScore").num
        (llm, avgScore, maxScore)
      }.toSeq
    }
  end requestContestLlmRanking

  private def requestContestLlmDetail(
    aiModelId: String,
    contestSlug: String,
    serverRegion: ServerRegion,
  ): Future[(acRate: Double, avgTriedTimes: Double)] =
    requestContestLlmDetailRaw(aiModelId, contestSlug, serverRegion).map { response =>
      val json          = ujson.read(response)
      val detail        = json("data")("contestLlmDetail")
      val acRate        = detail("acRate").num
      val avgTriedTimes = detail("avgTriedTimes").num
      (acRate, avgTriedTimes)
    }
  end requestContestLlmDetail

  def upsertContestLlmData(
    contestSlug: String,
    serverRegion: ServerRegion = ServerRegion.Us,
  ): Future[Unit] =
    for
      rankingData <- requestContestLlmRanking(contestSlug, serverRegion)
      llms = rankingData.map(_.llm)
      // Fetch details for each LLM
      llmContestRankings <- runInBatchesAndCollect(rankingData) {
        case (llm, avgScore, maxScore) =>
          requestContestLlmDetail(llm.id.toString, contestSlug, serverRegion).map {
            case (acRate, avgTriedTimes) =>
              LlmContestRanking(
                llmId = llm.id,
                contestSlug = contestSlug,
                avgScore = avgScore,
                maxScore = maxScore,
                acRate = acRate,
                avgTriedTimes = avgTriedTimes,
              )
          }
      }
      // Upsert LLMs
      llmCount <- LlmRepository.upsert(llms)
      _ = info(s"Upserted $llmCount LLMs for contest $contestSlug")
      // Upsert LlmContestRankings
      rankingCount <- LlmContestRankingRepository.upsert(llmContestRankings)
      _ = info(s"Upserted $rankingCount LLM contest rankings for contest $contestSlug")
    yield ()
  end upsertContestLlmData

  def upsertAllContestLlmData(): Future[Unit] =
    for
      // LeetCode introduced LLM contest rankings starting from Weekly Contest 430
      contestSlugs <- ContestRepository.findTitleSlugsSince("weekly-contest-430")
      _            <- runInBatchesAndDiscard(contestSlugs)(upsertContestLlmData(_))
    yield ()

end LlmSourcing
