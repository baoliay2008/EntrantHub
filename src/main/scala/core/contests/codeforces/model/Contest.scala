package core.contests.codeforces.model


import java.time.Instant

import upickle.default.{ Reader, reader }

import util.TypeClasses.EntityIdMapping


case class Contest(
  id: Int,
  name: String,
  contestType: String, // ?TODO -> Enum: CF, IOI, ICPC
  phase: String,       // ?TODO -> Enum: BEFORE, CODING, PENDING_SYSTEM_TEST, SYSTEM_TEST, FINISHED
  frozen: Boolean,
  durationSeconds: Int,
  startTime: Option[Instant] = None,
  freezeDurationSeconds: Option[Int] = None,
  preparedBy: Option[String] = None,
  websiteUrl: Option[String] = None,
  description: Option[String] = None,
  difficulty: Option[Int] = None, // 1 to 5
  kind: Option[String] = None,
  icpcRegion: Option[String] = None,
  country: Option[String] = None,
  city: Option[String] = None,
  season: Option[String] = None,
  updatedAt: Instant = Instant.now(),
):
  def isGym: Boolean = id >= 100_000

end Contest


object Contest:
  type ContestId = Int

  given EntityIdMapping[Contest, ContestId] with
    extension (c: Contest)
      def getId: ContestId = c.id

  // Deserialize: Convert JSON -> Contest
  given Reader[Contest] = reader[ujson.Value].map { json =>
    // Retrieve optional String field from JSON object
    def getOptStr(field: String): Option[String] =
      json.obj.get(field).collect { case ujson.Str(value) => value }

    // Retrieve optional Int field from JSON object
    def getOptInt(field: String): Option[Int] =
      json.obj.get(field).collect { case ujson.Num(value) => value.toInt }

    // Retrieve optional Instant field from a numeric epoch seconds field
    def getOptInstantFromEpochSeconds(field: String): Option[Instant] =
      json.obj.get(field).collect { case ujson.Num(value) => Instant.ofEpochSecond(value.toLong) }

    Contest(
      id = json("id").num.toInt,
      name = json("name").str,
      contestType = json("type").str,
      phase = json("phase").str,
      frozen = json("frozen").bool,
      durationSeconds = json("durationSeconds").num.toInt,
      startTime = getOptInstantFromEpochSeconds("startTimeSeconds"),
      freezeDurationSeconds = getOptInt("freezeDurationSeconds"),
      preparedBy = getOptStr("preparedBy"),
      websiteUrl = getOptStr("websiteUrl"),
      description = getOptStr("description"),
      difficulty = getOptInt("difficulty"),
      kind = getOptStr("kind"),
      icpcRegion = getOptStr("icpcRegion"),
      country = getOptStr("country"),
      city = getOptStr("city"),
      season = getOptStr("season"),
    )
  }

end Contest
