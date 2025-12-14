package postgres


import scala.concurrent.{ ExecutionContext, Future }

import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api.*
import slick.lifted.ColumnOrdered

import util.Extensions.|>
import util.TypeClasses.EntityIdMapping


/** Generic base repository providing common CRUD and query utilities for any Slick-based table.
  *
  * @tparam Entity
  *   The case class representing the domain entity.
  * @tparam Id
  *   The type of the entity's unique identifier.
  * @tparam EntityTable
  *   The Slick table definition for the entity.
  */
trait Repository[Entity, Id, EntityTable <: Table[Entity]](
  using EntityIdMapping[Entity, Id]
):

  // Execution context and database instance
  final protected given ec: ExecutionContext           = ExecutionContext.global
  final protected val db: JdbcProfile#Backend#Database = PgConnector.db

  // The main Slick table query
  protected val tableQuery: TableQuery[EntityTable]

  /** Returns a `where` clause that filters table rows by a given ID.
    *
    * This method allows reusable filtering logic across multiple methods such as `deleteBy`,
    * `updateBy`, `findBy`, and `existsBy`. The matching function typically looks like:
    * `entityTable.id === id`.
    */
  protected def idMatcher(id: Id): EntityTable => Rep[Boolean]

  protected def entityMatcher(entity: Entity): EntityTable => Rep[Boolean] =
    idMatcher(entity.getId)

  def initSchema(): Future[Unit] =
    db.run(tableQuery.schema.createIfNotExists)

  final lazy val schemaName: Option[String] = tableQuery.baseTableRow.schemaName
  final lazy val tableName: String          = tableQuery.baseTableRow.tableName
  final lazy val fullTableName: String      = fullTableNameOf(tableName)

  protected def fullTableNameOf(tableName: String): String =
    schemaName.fold(tableName)(s => s"$s.$tableName")
  // Overloaded helper for TableQuery — retrieves the fully qualified table name
  // (e.g., when working with staging tables in plain SQL).
  protected def fullTableNameOf[T <: Table[?]](q: TableQuery[T]): String =
    fullTableNameOf(q.baseTableRow.tableName)

  private def queryBuilder(
    where: Option[EntityTable => Rep[Boolean]] = None,
    orderBy: Option[EntityTable => ColumnOrdered[?]] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None,
  ): Query[EntityTable, Entity, Seq] =
    tableQuery |>
      (q => where.fold(q)(q.filter)) |>
      (q => orderBy.fold(q)(q.sortBy)) |>
      (q => offset.fold(q)(q.drop)) |>
      (q => limit.fold(q)(q.take))
  end queryBuilder

  def insertOne(entity: Entity): Future[Int] =
    db.run(tableQuery += entity)

  def insert(entities: Seq[Entity]): Future[Int] =
    db.run(tableQuery ++= entities)
      .map(_.getOrElse(0))

  def upsertOne(entity: Entity): Future[Int] =
    db.run(tableQuery.insertOrUpdate(entity))

  def upsert(entities: Seq[Entity]): Future[Int] =
    // Unfortunately, Slick doesn't support insertOrUpdateAll for PostgreSQL :(
    // TODO:
    //   Concerned about potential performance issues with many upserts in a single transaction.
    //   Consider investigating batching to improve performance for large datasets (~40K records).
    val actions: Seq[DBIO[Int]] = entities.map(tableQuery.insertOrUpdate)
    val action: DBIO[Seq[Int]]  = DBIO.sequence(actions)
    db.run {
      action
        .transactionally
        .map(_.sum)
    }
  end upsert

  def delete(where: EntityTable => Rep[Boolean]): Future[Int] =
    val action = queryBuilder(where = Some(where))
      .delete
    db.run(action)

  def deleteAll(): Future[Int] =
    db.run(tableQuery.delete)

  def update(
    newEntity: Entity,
    where: EntityTable => Rep[Boolean],
  ): Future[Int] =
    val action = queryBuilder(where = Some(where))
      .update(newEntity)
    db.run(action)

  def updateColumns[Columns, Reps](
    set: EntityTable => Reps,
    newValues: Columns,
    where: EntityTable => Rep[Boolean],
  )(
    using shape: Shape[? <: FlatShapeLevel, Reps, Columns, ?]
  ): Future[Int] =
    val action = queryBuilder(where = Some(where))
      .map(set)
      .update(newValues)
    db.run(action)
  end updateColumns

  def findOne(
    where: Option[EntityTable => Rep[Boolean]] = None,
    orderBy: Option[EntityTable => ColumnOrdered[?]] = None,
  ): Future[Option[Entity]] =
    val action = queryBuilder(where = where, orderBy = orderBy, limit = Some(1))
      .result
      .headOption
    db.run(action)

  def find(
    where: Option[EntityTable => Rep[Boolean]] = None,
    orderBy: Option[EntityTable => ColumnOrdered[?]] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None,
  ): Future[Seq[Entity]] =
    val action = queryBuilder(where = where, orderBy = orderBy, limit = limit, offset = offset)
      .result
    db.run(action)

  def findColumns[Columns, Reps](
    select: EntityTable => Reps,
    where: Option[EntityTable => Rep[Boolean]] = None,
    orderBy: Option[EntityTable => ColumnOrdered[?]] = None,
    offset: Option[Int] = None,
    limit: Option[Int] = None,
  )(
    using shape: Shape[? <: FlatShapeLevel, Reps, Columns, ?]
  ): Future[Seq[Columns]] =
    val action = queryBuilder(where = where, orderBy = orderBy, limit = limit, offset = offset)
      .map(select)
      .result
    db.run(action)
  end findColumns

  def paginate(
    where: Option[EntityTable => Rep[Boolean]] = None,
    orderBy: Option[EntityTable => ColumnOrdered[?]] = None,
    page: Int,
    size: Int,
  ): Future[Seq[Entity]] =
    val offset = Some((page.max(1) - 1) * size)
    val limit  = Some(size)
    find(where = where, orderBy = orderBy, limit = limit, offset = offset)
  end paginate

  def exists(where: EntityTable => Rep[Boolean]): Future[Boolean] =
    val action = queryBuilder(where = Some(where))
      .exists
      .result
    db.run(action)

  def count(where: Option[EntityTable => Rep[Boolean]]): Future[Int] =
    val action = queryBuilder(where = where)
      .length
      .result
    db.run(action)

  def countAll: Future[Int] =
    count(where = None)

  private inline def toWhere(inline idOrEntity: Id | Entity): EntityTable => Rep[Boolean] =
    inline idOrEntity match
      case id: Id         => idMatcher(id)
      case entity: Entity => entityMatcher(entity)

  inline def deleteBy(inline idOrEntity: Id | Entity): Future[Int] =
    delete(where = toWhere(idOrEntity))

  inline def updateBy(
    inline idOrEntity: Id | Entity,
    newEntity: Entity,
  ): Future[Int] =
    update(newEntity = newEntity, where = toWhere(idOrEntity))

  inline def updateColumnsBy[Columns, Reps](
    inline idOrEntity: Id | Entity,
    set: EntityTable => Reps,
    newValues: Columns,
  )(
    using shape: Shape[? <: FlatShapeLevel, Reps, Columns, ?]
  ): Future[Int] =
    updateColumns(set = set, newValues = newValues, where = toWhere(idOrEntity))

  inline def findBy(inline idOrEntity: Id | Entity): Future[Option[Entity]] =
    findOne(where = Some(toWhere(idOrEntity)))

  inline def existsBy(inline idOrEntity: Id | Entity): Future[Boolean] =
    exists(where = toWhere(idOrEntity))

end Repository


/** A repository trait that provides staging table functionality for efficient bulk operations.
  *
  * This trait extends the base Repository to add support for temporary staging tables, enabling
  * high-performance bulk operations such as bulk synchronization and upserts. The staging table
  * pattern allows for atomic bulk operations by:
  *   - Creating a temporary table with identical schema to the main table
  *   - Performing bulk operations on the staging table
  *   - Transferring data from staging to main table in a single transaction
  *   - Automatically cleaning up staging tables after use
  *
  * @tparam Entity
  *   The entity type that this repository manages
  * @tparam Id
  *   The type of the entity's identifier
  * @tparam EntityTable
  *   The Slick table type for the entity, must extend Table[Entity]
  *
  * @note
  *   - Requires PostgreSQL as it uses PostgreSQL-specific features (UNLOGGED tables, LIKE clause)
  *   - Implementing classes must override `customTableQuery` to enable staging table creation
  *   - Staging tables are created as UNLOGGED for better performance (no WAL logging)
  *   - All bulk operations are transactional to ensure data consistency
  *
  * @see
  *   [[Repository]] for the base repository functionality
  */
trait StageableRepository[Entity, Id, EntityTable <: Table[Entity]](
  using EntityIdMapping[Entity, Id]
) extends Repository[Entity, Id, EntityTable]:

  /** Creates a TableQuery instance for a custom-named table, enabling staging table functionality.
    *
    * This abstract method '''MUST''' be implemented by concrete repositories to create TableQuery
    * instances that can work with dynamically named tables. The implementation should instantiate
    * the table class with the provided custom name instead of using the default table name.
    *
    * @param name
    *   The name of the staging table would be created in `withStagingTable` automatically
    *
    * @return
    *   A TableQuery instance configured to use the specified table name
    *
    * @example
    *   {{{
    * // For a RankingTable that accepts a custom table name:
    * protected def customTableQuery(name: String) =
    *   TableQuery[RankingTable](tag => RankingTable(tag, name))
    *   }}}
    *
    * @note
    *   - The table class must have a constructor accepting (Tag, String) parameters
    *   - The second parameter is used as the table name in the database
    *   - This method is called internally by `withStagingTable` to create staging tables
    *
    * @see
    *   [[withStagingTable]] for how this method is used to create staging tables
    */
  protected def customTableQuery(name: String): TableQuery[EntityTable]

  /** Executes database operations within the context of a temporary staging table.
    *
    * This method provides a managed environment for bulk operations by:
    *   - Creating a temporary UNLOGGED staging table with the same schema as the main table
    *   - Executing the provided operations on the staging table
    *   - Automatically cleaning up the staging table after operations complete
    *   - Ensuring all operations run within a single transaction
    *
    * The staging table is created with a unique name incorporating timestamp and random number to
    * avoid conflicts in concurrent operations. The table is created as UNLOGGED for better
    * performance since it's temporary and doesn't need WAL logging.
    *
    * @tparam T
    *   The type of the result returned by the operations
    *
    * @param operations
    *   A function that receives the staging TableQuery and the fully qualified staging table name,
    *   and returns a DBIO action. This function should contain all database operations to be
    *   performed on the staging table.
    *
    * @return
    *   A Future containing the result of the operations. The Future will fail if any operation
    *   fails, but the staging table will still be cleaned up due to transactional execution.
    *
    * @example
    *   {{{
    *   withStagingTable { (stagingTable, stagingTableName) =>
    *     for {
    *       // Insert data into staging table
    *       _ <- stagingTable ++= entities
    *       // Perform some transformation
    *       _ <- sqlu"UPDATE #$stagingTableName SET processed = true"
    *       // Copy to main table
    *       count <- sqlu"INSERT INTO main_table SELECT * FROM #$stagingTableName"
    *     } yield count
    *   }
    *   }}}
    *
    * @note
    *   - The staging table is automatically dropped even if operations fail
    *   - All operations run in a single transaction for consistency
    *   - The staging table name includes timestamp and random number for uniqueness
    *   - Uses PostgreSQL's LIKE clause to copy table structure including defaults
    *   - Staging table excludes indexes for better insert performance
    *
    * @see
    *   - [[bulkSync]] for an example of how this method is used for bulk synchronization
    *   - [[bulkUpsert]] for an example of how this method is used for bulk upserts
    *   - [[customTableQuery]] for the method that creates the staging TableQuery
    */
  def withStagingTable[T](
    operations: (TableQuery[EntityTable], String) => DBIO[T]
  ): Future[T] =
    val stagingTableName =
      s"${tableName}_staging_${System.currentTimeMillis()}_${scala.util.Random.nextInt(1000)}"
    val stagingTable         = customTableQuery(stagingTableName)
    val fullStagingTableName = fullTableNameOf(stagingTableName)

    val createStagingTable =
      sqlu"""
        CREATE UNLOGGED TABLE IF NOT EXISTS #$fullStagingTableName
        (LIKE #$fullTableName INCLUDING DEFAULTS EXCLUDING INDEXES)
      """
    val dropStagingTable =
      sqlu"DROP TABLE IF EXISTS #$fullStagingTableName"

    val action =
      for
        _      <- createStagingTable
        result <- operations(stagingTable, fullStagingTableName)
        _      <- dropStagingTable
      yield result
    db.run(action.transactionally)

  end withStagingTable

  /** Performs a bulk synchronization operation by replacing filtered records with new entities.
    *
    * This method implements a "delete and insert" pattern for bulk data synchronization:
    *   1. Inserts all provided entities into a staging table
    *   1. Optionally processes the staging data (e.g., validations, transformations)
    *   1. Deletes records from the main table matching the provided filter
    *   1. Inserts all records from the staging table into the main table
    *
    * This approach ensures atomic replacement of a subset of data while maintaining referential
    * integrity and avoiding partial updates.
    *
    * @param entities
    *   The sequence of entities to synchronize. If empty, the method returns immediately with a
    *   count of 0.
    *
    * @param deleteFilter
    *   A function that creates a filter condition for determining which records to delete from the
    *   main table. The function receives the main table and should return a Rep[Boolean]
    *   representing the WHERE clause.
    *
    * @param onStagingTable
    *   An optional function to perform additional operations on the staging table before the sync.
    *   Defaults to a no-op. Can be used for data validation, transformation.
    *
    * @return
    *   A Future containing the number of records inserted into the main table
    *
    * @example
    *   {{{
    *   // Sync all products for a specific category
    *   def syncCategoryProducts(categoryId: Long, products: Seq[Product]) =
    *     bulkSync(
    *       entities = products,
    *       deleteFilter = _.categoryId === categoryId,
    *     )
    *
    *   // Replace all records for a specific date
    *   def syncDailyReports(date: LocalDate, reports: Seq[Report]) =
    *     bulkSync(
    *       entities = reports,
    *       deleteFilter = _.reportDate === date
    *     )
    *   }}}
    *
    * @note
    *   - All operations are performed in a single transaction
    *   - The staging table is automatically created and dropped
    *   - If no entities are provided, no database operations are performed
    *   - The delete operation happens before the insert to avoid constraint violations
    *   - Consider the impact on foreign key constraints when using this method
    *
    * @see
    *   [[withStagingTable]] for the underlying staging table mechanism
    */
  def bulkSync(
    entities: Seq[Entity],
    deleteFilter: EntityTable => Rep[Boolean],
    onStagingTable: TableQuery[EntityTable] => DBIO[?] =
      (_: TableQuery[EntityTable]) => DBIO.successful(()),
  ): Future[Int] =
    if entities.isEmpty then
      Future.successful(0)
    else
      withStagingTable { (stagingTable, fullStagingTableName) =>
        for
          // 1. Bulk insert into staging table
          _ <- stagingTable ++= entities

          // 2. Optional pre-processing on staging data
          _ <- onStagingTable(stagingTable)

          // 3. Delete matching records from main table
          deletedCount <- tableQuery.filter(deleteFilter).delete

          // 4. Move all data from staging to main table
          insertedCount <-
            sqlu"""
              INSERT INTO #$fullTableName
              SELECT * FROM #$fullStagingTableName
            """
        yield insertedCount
      }
  end bulkSync

  /** Performs a bulk upsert operation, inserting new records and replacing existing ones.
    *
    * This method implements an efficient upsert pattern for bulk data by:
    *   1. Inserting all entities into a staging table
    *   1. Dynamically determining the primary key columns from the database schema
    *   1. Deleting existing records from the main table that match staging table PKs
    *   1. Inserting all records from the staging table into the main table
    *
    * This approach is more efficient than individual upserts for large datasets and ensures atomic
    * replacement of existing records while inserting new ones.
    *
    * @param entities
    *   The sequence of entities to upsert. If empty, the method returns immediately with a count of
    *   0.
    *
    * @return
    *   A Future containing the number of records inserted into the main table (includes both new
    *   records and replacements for existing records)
    *
    * @example
    *   {{{
    *   // Upsert a batch of users
    *   def updateOrCreateUsers(users: Seq[User]) =
    *     bulkUpsert(users).map { count =>
    *       println(s"Upserted $count users")
    *     }
    *
    *   // Bulk import with conflict resolution
    *   def importProducts(products: Seq[Product]) =
    *     for {
    *       count <- bulkUpsert(products)
    *       _ = info(s"Imported $count products, replaced existing ones")
    *     } yield count
    *   }}}
    *
    * @note
    *   - Requires that the table has a primary key constraint named `{tableName}_pkey`
    *   - The primary key columns are determined dynamically from the database schema
    *   - All operations are performed in a single transaction
    *   - Existing records with matching primary keys are completely replaced
    *   - For partial updates, consider using a different approach or custom SQL
    *
    * @see
    *   [[withStagingTable]] for the underlying staging table mechanism
    */
  def bulkUpsert(entities: Seq[Entity]): Future[Int] =
    if entities.isEmpty then
      Future.successful(0)
    else
      withStagingTable { (stagingTable, fullStagingTableName) =>
        for
          // 1. Bulk insert into staging table
          _ <- stagingTable ++= entities

          // 2. Get primary key columns for the JOIN condition
          pkJoinCondition <-
            sql"""
              SELECT string_agg('t.' || column_name || ' = s.' || column_name, ' AND ')
              FROM information_schema.key_column_usage
              WHERE table_schema = ${schemaName.getOrElse("public")}
                AND table_name = $tableName
                AND constraint_name = ${tableName + "_pkey"}
            """.as[String].head

          // 3. Delete existing rows with same PK
          deleted <-
            sqlu"""
              DELETE FROM #$fullTableName t
              USING #$fullStagingTableName s
              WHERE #$pkJoinCondition
            """

          // 4. Insert all from staging
          inserted <-
            sqlu"""
              INSERT INTO #$fullTableName
              SELECT * FROM #$fullStagingTableName
            """
        yield inserted
      }

end StageableRepository
