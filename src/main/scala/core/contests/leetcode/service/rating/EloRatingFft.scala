package core.contests.leetcode.service.rating


import scala.math.{ pow, sqrt }

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.TransformType.{ FORWARD, INVERSE }
import org.apache.commons.math3.transform.{ DftNormalization, FastFourierTransformer }


object EloRatingFft:

  private inline val RatingGranularity    = 100
  private inline val MinRating            = 0
  private inline val MaxRating            = 4000
  private inline val MaxRatingScaled      = MaxRating * RatingGranularity
  private inline val ConvolutionArraySize = 2 * MaxRatingScaled + 1
  private inline val MaxSigmaIndex        = 100

  // Precompute sigmaPrefixSum values up to MaxSigmaIndex
  private val sigmaPrefixSums: Array[Double] =
    val arr = Array.fill(MaxSigmaIndex + 1)(0.0)
    arr(0) = 1.0
    for i <- 1 to MaxSigmaIndex do
      arr(i) = pow(5.0 / 7.0, i) + arr(i - 1)
    arr

  private val fft          = FastFourierTransformer(DftNormalization.STANDARD)
  private val fftArraySize = 1 << (32 - Integer.numberOfLeadingZeros(ConvolutionArraySize - 1))

  private val winProbCurve: Array[Double] =
    (-MaxRatingScaled to MaxRatingScaled)
      .map(i => 1.0 / (1 + math.pow(10.0, i / (400.0 * RatingGranularity))))
      .toArray

  private val winProbPadded = winProbCurve ++ Array.fill(fftArraySize - winProbCurve.length)(0.0)
  private val winProbFft    = fft.transform(winProbPadded.map(Complex(_)), FORWARD)

  private def deltaCoefficient(attendedContestsCount: Int): Double =
    if attendedContestsCount > MaxSigmaIndex then 2.0 / 9.0
    else 1.0 / (1.0 + sigmaPrefixSums(attendedContestsCount))

  private def preCalcConvolution(ratings: Array[Double]): Array[Double] =
    val ratingHistogram = Array.fill(ConvolutionArraySize)(0.0)
    for rating <- ratings do
      val index = (rating * RatingGranularity).round.toInt
      ratingHistogram(index) += 1.0

    val ratingHistogramPadded = ratingHistogram ++
      Array.fill(fftArraySize - ratingHistogram.length)(0.0)

    val ratingHistogramFft    = fft.transform(ratingHistogramPadded.map(Complex(_)), FORWARD)
    val convolutionFreqDomain = winProbFft.lazyZip(ratingHistogramFft).map(_.multiply(_))
    val convolutionTimeDomain = fft.transform(convolutionFreqDomain, INVERSE).map(_.getReal)

    convolutionTimeDomain.take(ConvolutionArraySize)
  end preCalcConvolution

  private def binarySearchExpectedRating(
    convolution: Array[Double],
    meanRank: Double,
  ): Int =
    var lo = 0
    var hi = MaxRatingScaled
    while lo <= hi do
      val mid         = (lo + hi) >> 1
      val searchValue = convolution(mid + MaxRatingScaled) + 1.0
      if searchValue < meanRank then hi = mid - 1
      else lo = mid + 1
    lo
  end binarySearchExpectedRating

  private def expectedRating(
    rank: Int,
    rating: Double,
    convolution: Array[Double],
  ): Double =
    val ratingIndex  = (rating * RatingGranularity).round.toInt
    val expectedRank = convolution(ratingIndex + MaxRatingScaled) + 0.5
    val meanRank     = sqrt(expectedRank * rank)
    binarySearchExpectedRating(convolution, meanRank).toDouble / RatingGranularity

  def ratingAdjustments(
    ranks: Array[Int],
    ratings: Array[Double],
    attendedContestsCounts: Array[Int],
  ): (expectedRatings: Array[Double], deltas: Array[Double]) =
    require(
      ranks.length == ratings.length && ratings.length == attendedContestsCounts.length,
      s"All input arrays must have same length, but got lengths: " +
        s"ranks=${ranks.length}, " +
        s"ratings=${ratings.length}, " +
        s"attendedContestsCounts=${attendedContestsCounts.length}",
    )
    require(
      ratings.forall(r => r >= MinRating && r <= MaxRating),
      s"All ratings must be in the range [$MinRating, $MaxRating]",
    )
    require(attendedContestsCounts.forall(_ >= 0), "All attendedContestsCounts must be >= 0")

    val convolution  = preCalcConvolution(ratings)
    val coefficients = attendedContestsCounts.map(deltaCoefficient)

    val expectedRatings = ranks
      .zip(ratings)
      .map(expectedRating(_, _, convolution))
    val deltas = expectedRatings
      .zip(ratings)
      .map(_ - _)
    val weightedDeltas = deltas
      .zip(coefficients)
      .map(_ * _)
    (expectedRatings, weightedDeltas)
  end ratingAdjustments

  def computeRealTimeRatingsMatrix(
    ratings: Array[Double],
    attendedContestsCounts: Array[Int],
    realTimeRanksMatrix: Array[Array[Int]],
  ): Array[Array[Double]] =
    // I skip all `require()` checks here because this method is generally used after
    // `ratingAdjustments`, which has already done checks
    val convolution  = preCalcConvolution(ratings)
    val coefficients = attendedContestsCounts.map(deltaCoefficient)
    ratings
      .indices
      .map { i =>
        val rating                = ratings(i)
        val attendedContestsCount = attendedContestsCounts(i)
        val coefficient           = coefficients(i)
        val realTimeRanks         = realTimeRanksMatrix(i)
        val realTimeRatings = realTimeRanks.map { rank =>
          val delta         = expectedRating(rank, rating, convolution) - rating
          val weightedDelta = delta * coefficient
          rating + weightedDelta
        }
        realTimeRatings
      }.toArray
  end computeRealTimeRatingsMatrix

end EloRatingFft
