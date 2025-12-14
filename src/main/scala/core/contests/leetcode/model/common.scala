package core.contests.leetcode.model

import util.Logging


/** Represents a server region used for making requests in the application.
  *
  * @param baseUrl
  *   The base URL of the server.
  * @param dataRegion
  *   The corresponding data region used by the LeetCode API.
  *
  * Notes:
  *   - `dataRegion` maps to the "data_region" field used in LeetCode's API.
  *   - `dataRegion` and `ServerRegion` are not always equivalent. For instance, `dataRegion` may
  *     include values like "LY" beyond just "CN" and "US".
  *   - In some cases, the `ServerRegion` value can be used directly as the `dataRegion`.
  */
enum ServerRegion(
  val baseUrl: String,
  val dataRegion: String,
):
  case Cn extends ServerRegion("https://leetcode.cn", "CN")
  case Us extends ServerRegion("https://leetcode.com", "US")

  def contestHomepageUrl: String                   = s"$baseUrl/contest/"
  def contestInfoUrl(titleSlug: String): String    = s"$baseUrl/contest/api/info/$titleSlug/"
  def contestRankingUrl(titleSlug: String): String = s"$baseUrl/contest/api/ranking/$titleSlug/"
  def graphQLUrl: String                           = s"$baseUrl/graphql/"
  def graphQLGoUrl: String                         = s"$baseUrl/graphql/noj-go/"

end ServerRegion


object ServerRegion extends Logging:
  def fromDataRegion(dataRegion: String): ServerRegion =
    dataRegion.toUpperCase match
      case Cn.dataRegion => Cn
      case Us.dataRegion => Us
      case _ =>
        warn(s"unknown dataRegion=$dataRegion, treating it as US region by default")
        Us
end ServerRegion


enum ContestType:
  case Weekly, Biweekly
