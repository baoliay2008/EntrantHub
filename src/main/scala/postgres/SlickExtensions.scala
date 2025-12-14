package postgres

import slick.jdbc.PostgresProfile.api.Query


object SlickExtensions:

  extension [E, U, C[_]](query: Query[E, U, C])
    def paginate(limit: Option[Int], offset: Option[Int]): Query[E, U, C] =
      (offset, limit) match
        case (Some(off), Some(lim)) => query.drop(off).take(lim)
        case (Some(off), None)      => query.drop(off)
        case (None, Some(lim))      => query.take(lim)
        case (None, None)           => query
