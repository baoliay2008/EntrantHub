package core.contests.leetcode.service


import java.time.DayOfWeek.{ SATURDAY, SUNDAY }
import java.util.concurrent.ScheduledFuture

import core.contests.leetcode.model.ContestType.{ Biweekly, Weekly }
import core.contests.leetcode.service.OngoingContest.{
  predictCurrentBoth,
  predictCurrent,
  prefetchCurrent,
}
import core.contests.leetcode.service.sourcing.ContestQuestionSourcing.upsertRecentContestsAndQuestions
import core.contests.leetcode.service.sourcing.UserSourcing.upsertAllUsers
import scheduler.{ HourlyJobPlan, Scheduler, WeeklyJobPlan }


object LeetCodeScheduler
    extends Scheduler:

  override protected def hourlyJobPlans: List[HourlyJobPlan] =
    List(
      HourlyJobPlan(1, 0, "Upsert Recent Contests", () => upsertRecentContestsAndQuestions()),
      HourlyJobPlan(31, 0, "Upsert Recent Contests", () => upsertRecentContestsAndQuestions()),
    )

  override protected def weeklyJobPlans: List[WeeklyJobPlan] =
    val base = List(
      WeeklyJobPlan(SATURDAY, 7, 0, 0, "Upsert All Users", () => upsertAllUsers()),
      WeeklyJobPlan(SUNDAY, 2, 41, 0, "Prefetch Contest", () => prefetchCurrent(Weekly)),
      WeeklyJobPlan(SUNDAY, 3, 1, 0, "Prefetch Contest", () => prefetchCurrent(Weekly)),
      WeeklyJobPlan(SUNDAY, 3, 21, 0, "Prefetch Contest", () => prefetchCurrent(Weekly)),
      WeeklyJobPlan(SUNDAY, 3, 41, 0, "Prefetch Contest", () => prefetchCurrent(Weekly)),
      WeeklyJobPlan(SUNDAY, 4, 3, 0, "Predict Contest", () => predictCurrent(Weekly)),
      WeeklyJobPlan(SUNDAY, 4, 23, 0, "Predict Contest", () => predictCurrent(Weekly)),
      WeeklyJobPlan(SUNDAY, 4, 43, 0, "Predict Contest", () => predictCurrent(Weekly)),
    )

    val hours = 5 to 23
    val additional = hours.map { h =>
      WeeklyJobPlan(SUNDAY, h, 0, 0, "Predict Contest", () => predictCurrentBoth())
    }.toList
    base ::: additional
  end weeklyJobPlans

  private def startBiweeklyJobs(): List[ScheduledFuture[?]] =
    // Inside biweekly jobs, they will take care of non-biweekly weeks by simply skipping them
    // So here we can directly schedule them as weekly jobs
    val biweeklyJobPlans = List(
      WeeklyJobPlan(SATURDAY, 14, 41, 0, "Prefetch Contest", () => prefetchCurrent(Biweekly)),
      WeeklyJobPlan(SATURDAY, 15, 1, 0, "Prefetch Contest", () => prefetchCurrent(Biweekly)),
      WeeklyJobPlan(SATURDAY, 15, 21, 0, "Prefetch Contest", () => prefetchCurrent(Biweekly)),
      WeeklyJobPlan(SATURDAY, 15, 41, 0, "Prefetch Contest", () => prefetchCurrent(Biweekly)),
      WeeklyJobPlan(SATURDAY, 16, 3, 0, "Predict Contest", () => predictCurrent(Biweekly)),
      WeeklyJobPlan(SATURDAY, 16, 23, 0, "Predict Contest", () => predictCurrent(Biweekly)),
      WeeklyJobPlan(SATURDAY, 16, 43, 0, "Predict Contest", () => predictCurrent(Biweekly)),
    ) ::: (17 to 23).map { h =>
      WeeklyJobPlan(SATURDAY, h, 0, 0, "Predict Contest", () => predictCurrent(Biweekly))
    }.toList
    startWeeklyJobs(biweeklyJobPlans)
  end startBiweeklyJobs

  override def startJobs(): List[ScheduledFuture[?]] =
    info("Starting biweekly jobs")
    val extraJobs = startBiweeklyJobs()
    info("Started biweekly jobs")
    super.startJobs() ::: extraJobs

end LeetCodeScheduler
