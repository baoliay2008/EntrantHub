package util


import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class TimeUtilsSpec
    extends AnyWordSpec, Matchers:

  import TimeUtils.*

  "convertUnixSecondsToUtc" should {

    val validTimestampCases = List(
      // normal cases
      3_392_867_227L -> "2077-07-07 07:07:07",
      946_684_801L   -> "2000-01-01 00:00:01",
      946_684_800L   -> "2000-01-01 00:00:00",
      946_684_799L   -> "1999-12-31 23:59:59",
      1L             -> "1970-01-01 00:00:01",
      0L             -> "1970-01-01 00:00:00",
      -1L            -> "1969-12-31 23:59:59",
      // boundary cases
      MinUnixSeconds -> "0001-01-01 00:00:00",
      MaxUnixSeconds -> "9999-12-31 23:59:59",
    )

    for (input, expected) <- validTimestampCases do
      s"return [$expected] for [$input]" in {
        convertUnixSecondsToUtc(input) shouldBe expected
      }

    val outOfRangeTimestamps = List(
      MinUnixSeconds - 1L,
      MaxUnixSeconds + 1L,
    )

    for unixSeconds <- outOfRangeTimestamps do
      s"throw IllegalArgumentException for out-of-range input [$unixSeconds]" in {
        val ex = intercept[IllegalArgumentException] {
          convertUnixSecondsToUtc(unixSeconds)
        }
        ex.getMessage should include("requirement failed:")
      }
  }
end TimeUtilsSpec
