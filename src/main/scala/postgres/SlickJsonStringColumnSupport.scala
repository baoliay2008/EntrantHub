package postgres


import slick.jdbc.PostgresProfile.api.*
import upickle.default.{ ReadWriter, read, write }


object SlickJsonStringColumnSupport:

  given listColumnType[T: ReadWriter]: BaseColumnType[List[T]] =
    MappedColumnType.base[List[T], String](
      list => write(list),
      json => read[List[T]](json),
    )

  // Predefined List column type instances for common types
  given stringListColumnType: BaseColumnType[List[String]] =
    listColumnType[String]

end SlickJsonStringColumnSupport
