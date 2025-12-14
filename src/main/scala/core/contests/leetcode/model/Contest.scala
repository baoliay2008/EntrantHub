package core.contests.leetcode.model


import java.time.Instant

import util.TypeClasses.EntityIdMapping


case class Contest(
  titleSlug: String,
  startTime: Instant,
  durationSeconds: Int,
  titleUs: String,
  titleCn: String,
  unratedUs: Boolean,
  unratedCn: Boolean,
  rankingUpdatedUs: Boolean,
  rankingUpdatedCn: Boolean,
  registerUserNumUs: Int,
  registerUserNumCn: Int,
  userNumUs: Option[Int],
  userNumCn: Option[Int],
  discussUrlUs: Option[String],
  discussUrlCn: Option[String],
  updatedAt: Instant = Instant.now(),
  predictedAt: Option[Instant] = None,
):
  import Contest.{ parseTitleSlug, topicIdPattern }

  lazy val (contestType: Option[ContestType], contestNum: Option[Int]) = parseTitleSlug(titleSlug)

  def userNumTotal: Option[Int] = userNumUs.flatMap(us => userNumCn.map(us + _))

  def urlUs: String = s"${ServerRegion.Us.baseUrl}/contest/$titleSlug/ranking/"
  def urlCn: String = s"${ServerRegion.Cn.baseUrl}/contest/$titleSlug/ranking/"

  def discussUrlFullUs: Option[String] = discussUrlUs.map(ServerRegion.Us.baseUrl + _)
  def discussUrlFullCn: Option[String] = discussUrlCn.map(ServerRegion.Cn.baseUrl + _)

  def topicIdUs: Option[String] = discussUrlUs.collect { case topicIdPattern(id) => id }
  def topicIdCn: Option[String] = discussUrlCn.collect { case topicIdPattern(id) => id }

end Contest


object Contest:
  type ContestId = String

  given EntityIdMapping[Contest, ContestId] with
    extension (c: Contest)
      def getId: ContestId = c.titleSlug

  private val titleSlugPattern = """(weekly|biweekly)-contest-(\d+)""".r
  private val topicIdPattern   = """/discuss/post/(\d+)/.*""".r

  private[model] def parseTitleSlug(contestTitleSlug: String): (Option[ContestType], Option[Int]) =
    contestTitleSlug.toLowerCase match
      case titleSlugPattern("weekly", num)   => (Some(ContestType.Weekly), Some(num.toInt))
      case titleSlugPattern("biweekly", num) => (Some(ContestType.Biweekly), Some(num.toInt))
      case _                                 => (None, None)

  /** Biweekly Contest 1 started: Jun 1, 2019, 7:30 AM PDT
    *
    * Weekly Contest 139 started: Jun 1, 2019, 7:30 PM PDT
    *
    * Every odd-numbered weekly contest ('''≥ 139''') had a biweekly contest 12 hours earlier.
    * LeetCode updates the ratings for these two contests together.
    *
    * When backfilling old ratings, use the prediction from the '''biweekly''' contest to fill the
    * '''weekly''' rating (since the biweekly ends earlier).
    */
  private val WeeklyNumAfterFirstBiweeklyContest = 139

  /** Returns the slug of the most recent biweekly contest that occurred exactly 12 hours before the
    * given odd-numbered weekly contest.
    *
    * If there was no such contest, returns `None`.
    */
  private[leetcode] def precedingBiweeklySlugForOddWeek(contestTitleSlug: String): Option[String] =
    parseTitleSlug(contestTitleSlug) match
      case (Some(ContestType.Weekly), Some(i))
          if i >= WeeklyNumAfterFirstBiweeklyContest && i % 2 == 1 =>
        val biNum = ((i - WeeklyNumAfterFirstBiweeklyContest) >> 1) + 1
        Some(s"biweekly-contest-$biNum")
      case _ =>
        None

end Contest
