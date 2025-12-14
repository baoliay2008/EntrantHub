package core.contests.leetcode.service.sourcing


import scala.concurrent.{ ExecutionContext, Future }

import core.contests.leetcode.model.ServerRegion.{ Cn, Us }
import core.contests.leetcode.model.User.UserId
import core.contests.leetcode.model.{ ServerRegion, User }
import core.contests.leetcode.repository.UserRepository
import requests.HttpRequestManager.postJsonRequest
import util.FutureUtils.runInBatchesAndDiscard
import util.Logging


object UserSourcing extends Logging:
  private given ec: ExecutionContext = ExecutionContext.global

  private type UserProfile        = (realName: String, userAvatar: String)
  private type UserContestRanking = (attendedContestsCount: Int, rating: Double, globalRanking: Int)

  private def requestUserProfileUs(userSlug: String): Future[Option[UserProfile]] =
    val url = Us.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query userPublicProfile($username: String!) {
          |  matchedUser(username: $username) {
          |    profile {
          |      realName
          |      userAvatar
          |    }
          |  }
          |}""".stripMargin,
      "variables"     -> Map("username" -> userSlug),
      "operationName" -> "userPublicProfile",
    )
    val responseFuture = postJsonRequest(url, jsonBody = jsonBody)
    responseFuture.map { response =>
      val matchedUser = ujson.read(response)("data")("matchedUser")
      Option.unless(matchedUser.isNull) {
        (
          matchedUser("profile")("realName").str,
          matchedUser("profile")("userAvatar").str,
        )
      }
    }
  end requestUserProfileUs

  private def requestUserProfileCn(userSlug: String): Future[Option[UserProfile]] =
    val url = Cn.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query userProfilePublicProfile($userSlug: String!) {
          |  userProfilePublicProfile(userSlug: $userSlug) {
          |    profile {
          |      realName
          |      userAvatar
          |    }
          |  }
          |}""".stripMargin,
      "variables"     -> Map("userSlug" -> userSlug),
      "operationName" -> "userProfilePublicProfile",
    )
    val responseFuture = postJsonRequest(url, jsonBody = jsonBody)
    responseFuture.map { response =>
      val userProfilePublicProfile = ujson.read(response)("data")("userProfilePublicProfile")
      Option.unless(userProfilePublicProfile.isNull) {
        (
          userProfilePublicProfile("profile")("realName").str,
          userProfilePublicProfile("profile")("userAvatar").str,
        )
      }
    }
  end requestUserProfileCn

  private def requestUserProfile(
    serverRegion: ServerRegion,
    userSlug: String,
  ): Future[Option[UserProfile]] = serverRegion match
    case Us => requestUserProfileUs(userSlug)
    case Cn => requestUserProfileCn(userSlug)

  private def requestUserContestRanking(
    serverRegion: ServerRegion,
    userSlug: String,
  ): Future[Option[UserContestRanking]] =
    val (url, jsonBody) = serverRegion match
      case ServerRegion.Cn =>
        val url = serverRegion.graphQLGoUrl
        val jsonBody = ujson.Obj(
          "query" ->
            """query userContestRankingInfo($userSlug: String!) {
              |    userContestRanking(userSlug: $userSlug) {
              |        attendedContestsCount
              |        rating
              |        globalRanking
              |    }
              |}""".stripMargin,
          "variables" -> Map("userSlug" -> userSlug),
        )
        (url, jsonBody)
      case ServerRegion.Us =>
        val url = serverRegion.graphQLUrl
        val jsonBody = ujson.Obj(
          "query" ->
            """query getContestRankingData($username: String!) {
              |   userContestRanking(username: $username) {
              |       attendedContestsCount
              |       rating
              |       globalRanking
              |   }
              |}""".stripMargin,
          "variables" -> Map("username" -> userSlug),
        )
        (url, jsonBody)
    val responseFuture = postJsonRequest(url, jsonBody = jsonBody)
    responseFuture.map { response =>
      val userContestRankingJson = ujson.read(response)("data")("userContestRanking")
      Option.unless(userContestRankingJson.isNull) {
        (
          userContestRankingJson("attendedContestsCount").num.toInt,
          userContestRankingJson("rating").num,
          userContestRankingJson("globalRanking").num.toInt,
        )
      }
    }
  end requestUserContestRanking

  def requestUser(userId: UserId): Future[Option[User]] =
    val serverRegion = ServerRegion.fromDataRegion(userId.dataRegion)
    for
      profileOpt <- requestUserProfile(serverRegion, userId.userSlug)
      userOpt <- profileOpt match
        case Some((realName, avatarURL)) =>
          for
            contestRanking <- requestUserContestRanking(serverRegion, userId.userSlug)
            user = User(
              dataRegion = userId.dataRegion,
              userSlug = userId.userSlug.toLowerCase, // force userSlug to be case-insensitive
              realName = realName,
              avatarUrl = avatarURL,
              attendedContestsCount = contestRanking.map(_.attendedContestsCount),
              rating = contestRanking.map(_.rating),
              globalRanking = contestRanking.map(_.globalRanking),
            )
          yield Some(user)
        case None => Future.successful(None)
    yield userOpt
    end for
  end requestUser

  private def requestUsersRawByPage(pageNum: Int = 1): Future[String] =
    val url = Us.graphQLUrl
    val jsonBody = ujson.Obj(
      "query" ->
        """query globalRanking($page: Int!) {
          |  globalRanking(page: $page) {
          |    totalUsers
          |    userPerPage
          |    rankingNodes {
          |      ranking
          |      currentRating
          |      currentGlobalRanking
          |      dataRegion
          |      user {
          |        profile {
          |          userSlug
          |          userAvatar
          |          realName
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin,
      "variables"     -> Map("page" -> pageNum),
      "operationName" -> "globalRanking",
    )
    postJsonRequest(url, jsonBody = jsonBody)
  end requestUsersRawByPage

  private def requestUsersByPage(pageNum: Int = 1): Future[Seq[User]] =
    val rawPageResponseFuture = requestUsersRawByPage(pageNum)
    rawPageResponseFuture.map { response =>
      val rankingNodesJson = ujson.read(response)("data")("globalRanking")("rankingNodes")
      Option.unless(rankingNodesJson.isNull) {
        rankingNodesJson.arr.map { rankingNode =>
          User(
            dataRegion = rankingNode("dataRegion").str,
            userSlug = rankingNode("user")("profile")("userSlug").str.toLowerCase,
            realName = rankingNode("user")("profile")("realName").str,
            avatarUrl = rankingNode("user")("profile")("userAvatar").str,
            attendedContestsCount = Some(ujson.read(rankingNode("ranking").str).arr.length),
            rating = Some(ujson.read(rankingNode("currentRating").str).num),
            globalRanking = Some(rankingNode("currentGlobalRanking").num.toInt),
          )
        }.toSeq
      }.getOrElse(Seq.empty)
    }
  end requestUsersByPage

  private def requestMaxPageNum(): Future[Int] =
    val rawPageResponseFuture = requestUsersRawByPage()
    rawPageResponseFuture.map { response =>
      val globalRankingJson = ujson.read(response)("data")("globalRanking")
      val totalUsers        = globalRankingJson("totalUsers").num.toInt
      val userPerPage       = globalRankingJson("userPerPage").num.toInt
      val maxPageNum        = (totalUsers + userPerPage - 1) / userPerPage
      maxPageNum
    }

  private def requestAllUsersLazily(): Future[Iterator[Future[Seq[User]]]] =
    val maxPageNumFuture = requestMaxPageNum()
    maxPageNumFuture.map { maxPageNum =>
      (1 to maxPageNum).iterator.map { pageNum =>
        requestUsersByPage(pageNum)
      }
    }

  def upsertAllUsers(): Future[Unit] =
    val onBatch: Future[Seq[User]] => Future[Unit] = pageFuture =>
      for
        users      <- pageFuture
        usersCount <- UserRepository.upsert(users)
        _ = debug(s"upserted $usersCount users")
      yield ()
    for
      page <- requestAllUsersLazily()
      _    <- runInBatchesAndDiscard(page)(onBatch)
    yield ()
  end upsertAllUsers

end UserSourcing
