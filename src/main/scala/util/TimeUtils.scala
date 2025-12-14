package util


import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDateTime, ZoneOffset }


object TimeUtils:

  // Constants for valid range (0001-01-01 00:00:00 UTC to 9999-12-31 23:59:59 UTC)
  private val MinDateTime = LocalDateTime.of(1, 1, 1, 0, 0, 0)
  private val MaxDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59)

  // Constants for valid Unix timestamp range
  val MinUnixSeconds: Long = MinDateTime.toEpochSecond(ZoneOffset.UTC) // -62_135_596_800L
  val MaxUnixSeconds: Long = MaxDateTime.toEpochSecond(ZoneOffset.UTC) // 253_402_300_799L

  private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

  /** Converts a Unix timestamp to a formatted UTC string.
    *
    * @param unixSeconds
    *   The Unix timestamp to convert.
    * @return
    *   A string formatted as "yyyy-MM-dd HH:mm:ss" in UTC.
    * @throws IllegalArgumentException
    *   If the timestamp is outside the allowed range ["0001-01-01 00:00:00" and "9999-12-31
    *   23:59:59"].
    */
  def convertUnixSecondsToUtc(unixSeconds: Long): String =
    require(
      unixSeconds >= MinUnixSeconds && unixSeconds <= MaxUnixSeconds,
      "Unix timestamp must be between 0001-01-01 00:00:00 and 9999-12-31 23:59:59 UTC.",
    )
    formatter.format(Instant.ofEpochSecond(unixSeconds))

end TimeUtils
