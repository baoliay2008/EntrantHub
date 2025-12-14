package postgres


import scala.reflect.ClassTag

import slick.jdbc.{ GetResult, SetParameter }


object SlickPlainSqlSupport:

  /** A generic, reusable helper to create a `GetResult` for a nullable JDBC column.
    *
    * This allows Slick to safely map SQL `NULL` values to `Option[T]` in Scala.
    *
    * @param converter
    *   A function to convert a non-null JDBC object of type `J` to the desired Scala type `T`
    *   (e.g., from `java.lang.Integer` to `Int` or `java.lang.Double` to `Double`).
    * @tparam J
    *   The Java object type returned by the JDBC driver for this column. Examples:
    *   `java.lang.Integer` for SQL `INTEGER`, `java.lang.Double` for SQL `DOUBLE PRECISION`.
    * @tparam T
    *   The target Scala type to wrap in `Option` (e.g., `Int`, `Double`).
    * @return
    *   A `GetResult` instance that converts `NULL` values to `None` and non-null values using the
    *   provided converter function.
    */
  private def nullableGetResult[J <: AnyRef, T](converter: J => T): GetResult[Option[T]] =
    positionedResult =>
      Option(positionedResult.nextObject().asInstanceOf[J]).map(converter)

  // --- Implicit GetResult Instances for Nullable Columns ---
  //
  // These `given` instances allow Slick to automatically convert nullable SQL columns into
  // Scala `Option` types when using `.as[...]`.

  given booleanOptionGetResult: GetResult[Option[Boolean]] =
    nullableGetResult[java.lang.Boolean, Boolean](_.booleanValue())

  given intOptionGetResult: GetResult[Option[Int]] =
    nullableGetResult[java.lang.Integer, Int](_.toInt)

  given longOptionGetResult: GetResult[Option[Long]] =
    nullableGetResult[java.lang.Long, Long](_.toLong)

  given doubleOptionGetResult: GetResult[Option[Double]] =
    nullableGetResult[java.lang.Double, Double](_.toDouble)

  given stringOptionGetResult: GetResult[Option[String]] =
    nullableGetResult[java.lang.String, String](identity)

  /** A generic, reusable helper to create a `GetResult` for a `Seq[T]` from a native SQL `ARRAY`.
    *
    * @param converter
    *   A function to convert an element from its Java type `J` to the desired Scala type `T` (e.g.,
    *   from `java.lang.Integer` to `Int`).
    * @tparam J
    *   The corresponding Java object type that the JDBC driver returns for elements in the SQL
    *   `ARRAY`. Examples: `java.lang.Integer` for SQL `INTEGER`, `java.lang.String` for `VARCHAR`.
    * @tparam T
    *   The target Scala type for elements in the resulting sequence (e.g., `Int`, `Long`).
    * @return
    *   A `GetResult` instance capable of mapping a SQL `ARRAY` column to a `Seq[T]`.
    */
  private def arrayGetResult[J <: AnyRef: ClassTag, T](converter: J => T): GetResult[Seq[T]] =
    positionedResult =>
      val sqlArray = positionedResult.nextObject().asInstanceOf[java.sql.Array]
      Option(sqlArray) match
        case Some(a) =>
          val javaArray = a.getArray.asInstanceOf[Array[J]]
          javaArray.toVector.map(converter)
        case None =>
          Vector.empty[T]

  // --- Implicit GetResult Instances for ARRAY columns ---
  //
  // Provide `given` instances to be automatically imported and used by Slick's `.as[...]`
  // interpolator for common array types.

  given intSeqGetResult: GetResult[Seq[Int]] =
    arrayGetResult[java.lang.Integer, Int](_.toInt)

  given longSeqGetResult: GetResult[Seq[Long]] =
    arrayGetResult[java.lang.Long, Long](_.toLong)

  given doubleSeqGetResult: GetResult[Seq[Double]] =
    arrayGetResult[java.lang.Double, Double](_.toDouble)

  given stringSeqGetResult: GetResult[Seq[String]] =
    arrayGetResult[java.lang.String, String](identity)

  /** A generic, reusable helper to create a SetParameter for a Seq[T]. This tells Slick how to bind
    * a Scala `Seq` to a `PreparedStatement` for a SQL `ARRAY` column.
    *
    * @param sqlTypeName
    *   The name of the SQL array type (e.g., `integer`, `bigint`, `varchar`). This is required by
    *   the JDBC API to create a SQL Array.
    * @tparam T
    *   The Scala element type (e.g., `Int`, `Long`, `String`).
    */
  private def seqSetParameter[T](sqlTypeName: String): SetParameter[Seq[T]] =
    (seq, positionedParameters) =>
      val preparedStatement = positionedParameters.ps
      val conn              = preparedStatement.getConnection
      val javaArray         = seq.map(_.asInstanceOf[AnyRef]).toArray
      val sqlArray          = conn.createArrayOf(sqlTypeName, javaArray)
      positionedParameters.setObject(sqlArray, java.sql.Types.ARRAY)

  // --- Implicit SetParameter instances for common array types ---

  given seqIntSetParameter: SetParameter[Seq[Int]] =
    seqSetParameter[Int]("integer")

  given seqLongSetParameter: SetParameter[Seq[Long]] =
    seqSetParameter[Long]("bigint")

  given seqDoubleSetParameter: SetParameter[Seq[Double]] =
    seqSetParameter[Double]("double precision")

  given seqStringSetParameter: SetParameter[Seq[String]] =
    seqSetParameter[String]("varchar") // "text" may also work

end SlickPlainSqlSupport
