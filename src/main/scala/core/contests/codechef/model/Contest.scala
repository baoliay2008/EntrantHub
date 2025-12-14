package core.contests.codechef.model


import java.time.Instant

import upickle.default.{ Reader, reader }

import util.TypeClasses.EntityIdMapping


case class Contest(
  id: String,
  name: String,
  startTime: Instant,
  endTime: Instant,
  durationSeconds: Int,
  distinctUsers: Int,
  updatedAt: Instant = Instant.now(),
)


object Contest:
  type ContestId = String

  given EntityIdMapping[Contest, ContestId] with
    extension (c: Contest)
      def getId: ContestId = c.id

  given Reader[Contest] = reader[ujson.Value].map { json =>
    Contest(
      id = json("contest_code").str,
      name = json("contest_name").str,
      startTime = Instant.parse(json("contest_start_date_iso").str),
      endTime = Instant.parse(json("contest_end_date_iso").str),
      durationSeconds = json("contest_duration").str.toInt * 60,
      distinctUsers = json("distinct_users").num.toInt,
    )
  }

end Contest
