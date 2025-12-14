package core.contests.leetcode.service.rating


import java.util.zip.GZIPInputStream

import scala.io.Source
import scala.math.abs
import scala.util.Using

import org.scalatest.wordspec.AnyWordSpec


class EloRatingFftSpec extends AnyWordSpec:

  private val RatingDeltaPrecision = 0.05

  private def loadContestPredictionData(fileName: String)
    : (Array[Int], Array[Int], Array[Double], Array[Double]) =
    val inputStream = getClass.getResourceAsStream(s"/$fileName")
    require(inputStream != null, s"Resource $fileName not found")

    Using.resource(Source.fromInputStream(GZIPInputStream(inputStream))) { source =>
      val lines                  = source.getLines().toArray
      val parsed                 = lines.map(_.split(",").map(_.toDouble))
      val attendedContestsCounts = parsed.map(_(0).toInt)
      val ranks                  = parsed.map(_(1).toInt)
      val oldRatings             = parsed.map(_(2))
      val expectedNewRatings     = parsed.map(_(3))
      (attendedContestsCounts, ranks, oldRatings, expectedNewRatings)
    }
  end loadContestPredictionData

  "EloRatingFft" should {

    "predict new ratings within acceptable precision" in {

      val (attendedContestsCounts, ranks, oldRatings, expectedNewRatings) =
        loadContestPredictionData("contest_prediction_1.txt.gz")

      val (expectedRatings, deltas) =
        EloRatingFft.ratingAdjustments(ranks, oldRatings, attendedContestsCounts)
      val predictedNewRatings = oldRatings.zip(deltas).map(_ + _)
      val errors = predictedNewRatings.zip(expectedNewRatings).map { (predicted, actual) =>
        abs(predicted - actual)
      }

      assert(
        errors.forall(_ < RatingDeltaPrecision),
        f"Elo delta test failed. Max error: ${errors.max}%.4f, threshold: $RatingDeltaPrecision%.4f",
      )
    }

  }
end EloRatingFftSpec
