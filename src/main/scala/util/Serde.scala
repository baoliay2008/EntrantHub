package util


import java.time.{ Instant, LocalDate }

import upickle.default.{ ReadWriter, readwriter }


object Serde:

  given instantReadWriter: ReadWriter[Instant] = readwriter[String].bimap[Instant](
    _.toString,   // Serialize: Convert Instant to String
    Instant.parse, // Deserialize: Parse String back to Instant
  )

  given localDateReadWriter: ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](
    _.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
    LocalDate.parse,
  )

end Serde
