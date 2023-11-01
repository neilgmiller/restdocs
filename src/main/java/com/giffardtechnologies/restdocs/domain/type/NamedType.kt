package com.giffardtechnologies.restdocs.domain.type

/**
 * An interface for types that have an identifying name.
 */
interface NamedType {
    /**
     * Returns the human-readable type name that servers as an identifier (unrelated to the data type).
     *
     * @return the human-readable type name
     */
    val typeName: String

    /**
     * Returns the [TypeSpec] for this type
     *
     * @return the data type
     */
    val type: TypeSpec.Nameable
}
