package com.giffardtechnologies.restdocs.storage.type

/**
 * The full set of data types that a field, response, or type specification can have.
 *
 * Scalar types ([INT], [LONG], [FLOAT], [DOUBLE], [STRING], [BOOLEAN], [DATE]) represent single
 * values. Structural types ([ARRAY], [OBJECT], [COLLECTION]) contain nested types or fields.
 * [ENUM] and [BITSET] represent enumerated value sets.
 */
enum class DataType {
    INT, LONG, FLOAT, DOUBLE, STRING, BOOLEAN, DATE, ARRAY, OBJECT, COLLECTION, ENUM, BITSET;

    fun toBasicType(): BasicType {
        return when (this) {
            INT -> BasicType.INT
            LONG -> BasicType.LONG
            FLOAT -> BasicType.FLOAT
            DOUBLE -> BasicType.DOUBLE
            STRING -> BasicType.STRING
            BOOLEAN -> BasicType.BOOLEAN
            DATE,
            ARRAY,
            OBJECT,
            COLLECTION,
            ENUM,
            BITSET -> throw IllegalStateException("Cannot convert $this to a BasicType")
        }
    }
}

/**
 * The set of primitive types that a field's underlying wire representation can be interpreted as.
 *
 * Used with the `interpretedAs` property on [TypeSpec] to indicate a semantic re-interpretation
 * of a raw scalar (e.g., a [STRING] that is actually a date value).
 */
enum class BasicType {
    INT, LONG, FLOAT, DOUBLE, STRING, BOOLEAN
}