package com.giffardtechnologies.restdocs.storage.type

/**
 * An interface for types that have an identifying name.
 */
interface NamedType {
    /**
     * Returns the human-readable type name that servers as an identifier (unrelated to the data type).
     *
     * @return the human-readable type name
     */
    val typeName: String?

    /**
     * Returns the data type of this type, this will be [DataType.ENUM] or [DataType.OBJECT]
     *
     * @return the data type
     */
    val type: DataType?
}