package util


object TypeClasses:

  /** Type class for extracting an identifier from a core domain entity (i.e., the case class
    * itself), '''NOT''' from the Slick table or any other persistence representation.
    *
    * This is particularly useful when the identifier is a compound key or derived from multiple
    * fields within the entity.
    *
    * @tparam Entity
    *   The domain entity type, typically a case class.
    * @tparam Id
    *   The type representing the entity's unique identifier, often corresponding to the primary key
    *   type in a relational database.
    */
  trait EntityIdMapping[Entity, Id]:
    extension (entity: Entity) def getId: Id

end TypeClasses
