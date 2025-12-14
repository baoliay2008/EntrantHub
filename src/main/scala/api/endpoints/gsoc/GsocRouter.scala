package api.endpoints.gsoc


import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import api.common.{ CustomDirectives, Router }


object GsocRouter
    extends Router:

  private class InvalidYearException(year: Int, min: Int, max: Int)
      extends IllegalArgumentException(s"year=$year is invalid, must be between $min and $max")

  private val MinYear = 2016 // GSoC program data availability starts from 2016
  private val MaxYear = 2025

  /** Extracts and validates year parameter by throwing exception if invalid. */
  private def validateYear(year: Int): Unit =
    if year < MinYear || year > MaxYear then
      throw InvalidYearException(year, MinYear, MaxYear)

  def routes(
    using ExecutionContext
  ): Route =
    pathPrefix("gsoc") {
      categoriesRoute ~
        organizationsRoute ~
        projectsRoute
    }

  private def categoriesRoute(
    using ExecutionContext
  ): Route =
    path("categories") {
      get {
        onComplete(GsocHandler.getCategories()) {
          case Success(categories) =>
            complete(categories)
          case Failure(ex) =>
            onInternalError("Fetch categories", ex)
        }
      }
    }

  private def organizationsRoute(
    using ExecutionContext
  ): Route =
    path("organizations") {
      get {
        parameters("year".as[Int], "category".optional) { (year, categoryOpt) =>
          validateYear(year)
          CustomDirectives.withPagination() { pagination =>
            onComplete(
              GsocHandler.getOrganizations(
                year,
                categoryOpt,
                pagination.limit,
                pagination.offset,
              )
            ) {
              case Success(organizations) =>
                complete(organizations)
              case Failure(ex) =>
                onInternalError(s"Fetch organizations for year=$year, category=$categoryOpt", ex)
            }
          }
        }
      }
    }

  private def projectsRoute(
    using ExecutionContext
  ): Route =
    path("projects") {
      get {
        parameters("year".as[Int], "organization") { (year, organizationSlug) =>
          validateYear(year)
          onComplete(GsocHandler.getProjects(year, organizationSlug)) {
            case Success(projects) =>
              complete(projects)
            case Failure(ex) =>
              onInternalError(s"Fetch projects for year=$year, organization=$organizationSlug", ex)
          }
        }
      }
    }

end GsocRouter
