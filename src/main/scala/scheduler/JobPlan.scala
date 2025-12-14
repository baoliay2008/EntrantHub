package scheduler

import scala.concurrent.Future


/** Base trait for all time slot types (weekly, daily, hourly, minutely) */
sealed trait JobPlan:
  def second: Int
  def jobName: String
  def action: () => Future[Unit]


/** Minutely job (only second is configurable) */
final case class MinutelyJobPlan(
  second: Int = 0,
  jobName: String,
  action: () => Future[Unit],
) extends JobPlan


/** Hourly job (requires minute, second) */
final case class HourlyJobPlan(
  minute: Int,
  second: Int = 0,
  jobName: String,
  action: () => Future[Unit],
) extends JobPlan


/** Daily job (requires hour, minute, second) */
final case class DailyJobPlan(
  hour: Int,
  minute: Int,
  second: Int = 0,
  jobName: String,
  action: () => Future[Unit],
) extends JobPlan


/** Weekly job (requires dayOfWeek, hour, minute, second) */
final case class WeeklyJobPlan(
  dayOfWeek: java.time.DayOfWeek,
  hour: Int,
  minute: Int,
  second: Int = 0,
  jobName: String,
  action: () => Future[Unit],
) extends JobPlan
