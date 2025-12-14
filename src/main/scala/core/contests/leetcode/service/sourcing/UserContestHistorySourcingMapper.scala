package core.contests.leetcode.service.sourcing


import core.contests.leetcode
import core.contests.leetcode.model.{ ServerRegion, UserContestHistory }


private object UserContestHistorySourcingMapper:

  def toUserContestHistories(
    dto: UserContestHistorySourcingDto,
    serverRegion: ServerRegion,
    userSlug: String,
  ): List[UserContestHistory] =
    val items = dto.data.userContestRankingHistory
    items
      .getOrElse(List.empty)
      .filter(
        // ranking = 0 means invalid data
        item => item.attended && item.ranking != 0
      )
      .sortBy(_.contest.startTime)
      .foldLeft((List.empty[UserContestHistory], 1500.0, -1)) {
        case ((histories, lastRating, count), item) =>
          val newCount = count + 1
          val finishTimeInSeconds = serverRegion match
            case ServerRegion.Cn => (item.finishTimeInSeconds - item.contest.startTime).toInt
            case ServerRegion.Us => item.finishTimeInSeconds.toInt
          val history = UserContestHistory(
            dataRegion = serverRegion.dataRegion,
            userSlug = userSlug.toLowerCase,
            titleSlug = item.contest.title,
            finishTimeInSeconds = finishTimeInSeconds,
            ranking = item.ranking,
            newRating = item.rating,
            oldRating = lastRating,
            attendedContestsCount = newCount,
          )
          (histories :+ history, item.rating, newCount)
      }
      ._1
  end toUserContestHistories

end UserContestHistorySourcingMapper
