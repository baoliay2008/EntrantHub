package core.contests.leetcode.model


import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class ContestSpec
    extends AnyWordSpec, Matchers:

  import core.contests.leetcode.model.Contest.*

  "parseTitleSlug" should {

    val parseCases = List(
      // Weekly contests
      "weekly-contest-139" -> (Some(ContestType.Weekly), Some(139)),
      "WeEklY-ConTesT-200" -> (Some(ContestType.Weekly), Some(200)),
      // Biweekly contests
      "biweekly-contest-42" -> (Some(ContestType.Biweekly), Some(42)),
      "BiWeEkLy-CoNtEsT-10" -> (Some(ContestType.Biweekly), Some(10)),
      // Unrecognized formats
      "monthly-contest-1"  -> (None, None),
      "contest-weekly-139" -> (None, None),
      "weeklycontest-139"  -> (None, None),
      "weekly-contest-"    -> (None, None),
      "weekly-contest-abc" -> (None, None),
      "random-text"        -> (None, None),
      ""                   -> (None, None),
    )

    for (input, expected) <- parseCases do
      s"return [$expected] for [$input]" in {
        parseTitleSlug(input) shouldBe expected
      }

  }

  "lastConsecutiveContestTitleSlug" should {

    val lastSlugCases = List(
      // Valid odd-numbered weekly contests >= 139
      "weekly-contest-139" -> Some("biweekly-contest-1"),
      "weekly-contest-141" -> Some("biweekly-contest-2"),
      "weekly-contest-143" -> Some("biweekly-contest-3"),
      "WeEkLy-CoNtEsT-145" -> Some("biweekly-contest-4"),
      "WEEKLY-CONTEST-453" -> Some("biweekly-contest-158"),
      // Even-numbered weekly contests
      "weekly-contest-140" -> None,
      "weekly-contest-144" -> None,
      // Weekly contests before 139
      "weekly-contest-138" -> None,
      "weekly-contest-137" -> None,
      "weekly-contest-101" -> None,
      "weekly-contest-1"   -> None,
      // Biweekly contests
      "biweekly-contest-1"   -> None,
      "biweekly-contest-42"  -> None,
      "biweekly-contest-999" -> None,
      // Malformed or unrecognized
      "weeklycontest-139"  -> None,
      "weekly-contest-"    -> None,
      "biweekly-1"         -> None,
      "contest-weekly-139" -> None,
      "random-string"      -> None,
      ""                   -> None,
    )

    for (input, expected) <- lastSlugCases do
      s"return [$expected] for [$input]" in {
        precedingBiweeklySlugForOddWeek(input) shouldBe expected
      }

  }

end ContestSpec
