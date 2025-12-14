package scheduler


import java.util.concurrent.ScheduledFuture

import util.Logging


trait Scheduler extends Logging:

  protected def minutelyJobPlans: List[MinutelyJobPlan] = Nil
  protected def hourlyJobPlans: List[HourlyJobPlan]     = Nil
  protected def dailyJobPlans: List[DailyJobPlan]       = Nil
  protected def weeklyJobPlans: List[WeeklyJobPlan]     = Nil

  protected def startMinutelyJobs(
    jobPlans: List[MinutelyJobPlan] = minutelyJobPlans
  ): List[ScheduledFuture[?]] =
    jobPlans.map { jobPlan =>
      BackgroundJobs.scheduleMinutely(
        second = jobPlan.second,
        jobName = jobPlan.jobName,
      ) {
        jobPlan.action()
      }
    }

  protected def startHourlyJobs(
    jobPlans: List[HourlyJobPlan] = hourlyJobPlans
  ): List[ScheduledFuture[?]] =
    jobPlans.map { jobPlan =>
      BackgroundJobs.scheduleHourly(
        minute = jobPlan.minute,
        second = jobPlan.second,
        jobName = jobPlan.jobName,
      ) {
        jobPlan.action()
      }
    }

  protected def startDailyJobs(
    jobPlans: List[DailyJobPlan] = dailyJobPlans
  ): List[ScheduledFuture[?]] =
    jobPlans.map { jobPlan =>
      BackgroundJobs.scheduleDaily(
        hour = jobPlan.hour,
        minute = jobPlan.minute,
        second = jobPlan.second,
        jobName = jobPlan.jobName,
      ) {
        jobPlan.action()
      }
    }

  protected def startWeeklyJobs(
    jobPlans: List[WeeklyJobPlan] = weeklyJobPlans
  ): List[ScheduledFuture[?]] =
    jobPlans.map { jobPlan =>
      BackgroundJobs.scheduleWeekly(
        dayOfWeek = jobPlan.dayOfWeek,
        hour = jobPlan.hour,
        minute = jobPlan.minute,
        second = jobPlan.second,
        jobName = jobPlan.jobName,
      ) {
        jobPlan.action()
      }
    }

  /** Starts all background jobs and returns the scheduled futures. */
  def startJobs(): List[ScheduledFuture[?]] =
    info("Starting minutely, hourly, daily, weekly jobs")
    val jobs =
      startMinutelyJobs()
        ::: startHourlyJobs()
        ::: startDailyJobs()
        ::: startWeeklyJobs()
    info("Started minutely, hourly, daily, weekly jobs")
    jobs

end Scheduler
