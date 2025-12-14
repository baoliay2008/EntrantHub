package api.endpoints.contests


import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.{ ExecutionContext, Future }

import slick.jdbc.PostgresProfile.api.*

import ContestsDto.*
import core.contests.atcoder.model.Contest as AtCoderContest
import core.contests.atcoder.repository.ContestRepository as AtCoderRepo
import core.contests.codechef.model.Contest as CodeChefContest
import core.contests.codechef.repository.ContestRepository as CodeChefRepo
import core.contests.codeforces.model.Contest as CodeforcesContest
import core.contests.codeforces.repository.ContestRepository as CodeforcesRepo
import core.contests.leetcode.model.Contest as LeetCodeContest
import core.contests.leetcode.repository.ContestRepository as LeetCodeRepo
import util.Logging


object ContestsHandler extends Logging:

  /** Fetches all recent and upcoming contests from all platforms
    *
    * Returns contests that started within the last month or will start in the future
    */
  def getRecentAndUpcomingContests()(
    using ExecutionContext
  ): Future[List[UnifiedContestResponse]] =
    val oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS)

    // 1. Initiate all queries in Parallel (outside the for-comprehension)
    val leetcodeF = LeetCodeRepo.find(
      where = Some(_.startTime >= oneMonthAgo),
      orderBy = Some(_.startTime.desc),
    )
    val codeforcesF = CodeforcesRepo.find(
      // Codeforces startTime is Option[Instant], so we must unwrap it safely
      where = Some(t => (t.startTime >= oneMonthAgo).getOrElse(false)),
      orderBy = Some(_.startTime.desc),
    )
    val atcoderF = AtCoderRepo.find(
      where = Some(_.startTime >= oneMonthAgo),
      orderBy = Some(_.startTime.desc),
    )
    val codechefF = CodeChefRepo.find(
      where = Some(_.startTime >= oneMonthAgo),
      orderBy = Some(_.startTime.desc),
    )

    // 2. Aggregate results
    for
      leetcode   <- leetcodeF
      codeforces <- codeforcesF
      atcoder    <- atcoderF
      codechef   <- codechefF
    yield mergeAndSort(leetcode, codeforces, atcoder, codechef, descending = true)
  end getRecentAndUpcomingContests

  /** Fetches only upcoming contests from all platforms */
  def getUpcomingContests()(
    using ExecutionContext
  ): Future[List[UnifiedContestResponse]] =
    val now = Instant.now()

    // 1. Initiate all queries in Parallel (outside the for-comprehension)
    val leetcodeF = LeetCodeRepo.find(
      where = Some(_.startTime > now),
      orderBy = Some(_.startTime.asc),
    )
    val codeforcesF = CodeforcesRepo.find(
      where = Some(t => (t.startTime > now).getOrElse(false)),
      orderBy = Some(_.startTime.asc),
    )
    val atcoderF = AtCoderRepo.find(
      where = Some(_.startTime > now),
      orderBy = Some(_.startTime.asc),
    )
    val codechefF = CodeChefRepo.find(
      where = Some(_.startTime > now),
      orderBy = Some(_.startTime.asc),
    )

    // 2. Aggregate results
    for
      leetcode   <- leetcodeF
      codeforces <- codeforcesF
      atcoder    <- atcoderF
      codechef   <- codechefF
    yield mergeAndSort(leetcode, codeforces, atcoder, codechef, descending = false)
  end getUpcomingContests

  /** Fetches only past contests from the last month */
  def getPastContests()(
    using ExecutionContext
  ): Future[List[UnifiedContestResponse]] =
    val now         = Instant.now()
    val oneMonthAgo = now.minus(30, ChronoUnit.DAYS)

    // 1. Initiate all queries in Parallel (outside the for-comprehension)
    val leetcodeF = LeetCodeRepo.find(
      where = Some(t => t.startTime >= oneMonthAgo && t.startTime <= now),
      orderBy = Some(_.startTime.desc),
    )
    val codeforcesF = CodeforcesRepo.find(
      where = Some(t => (t.startTime >= oneMonthAgo && t.startTime <= now).getOrElse(false)),
      orderBy = Some(_.startTime.desc),
    )
    val atcoderF = AtCoderRepo.find(
      where = Some(t => t.startTime >= oneMonthAgo && t.startTime <= now),
      orderBy = Some(_.startTime.desc),
    )
    val codechefF = CodeChefRepo.find(
      where = Some(t => t.startTime >= oneMonthAgo && t.startTime <= now),
      orderBy = Some(_.startTime.desc),
    )

    // 2. Aggregate results
    for
      leetcode   <- leetcodeF
      codeforces <- codeforcesF
      atcoder    <- atcoderF
      codechef   <- codechefF
    yield mergeAndSort(leetcode, codeforces, atcoder, codechef, descending = true)
  end getPastContests

  /** Helper to merge and sort results from different sources */
  private def mergeAndSort(
    leetcode: Seq[LeetCodeContest],
    codeforces: Seq[CodeforcesContest],
    atcoder: Seq[AtCoderContest],
    codechef: Seq[CodeChefContest],
    descending: Boolean,
  ): List[UnifiedContestResponse] =
    val allContests =
      leetcode.map(ContestsMapper.fromLeetCode) ++
        codeforces.map(ContestsMapper.fromCodeforces) ++
        atcoder.map(ContestsMapper.fromAtCoder) ++
        codechef.map(ContestsMapper.fromCodeChef)
    val ordering =
      if descending then Ordering[Long].reverse
      else Ordering[Long]
    allContests
      .sortBy(_.startTime.getEpochSecond)(
        using ordering
      )
      .toList
  end mergeAndSort

end ContestsHandler
