package com.giffardtechnologies.restdocs.storage.type

/**
 * The integer storage type used to hold a combined set of bitset flag values.
 *
 * A [DataType.BITSET] field stores all active flags OR-ed together into a single integer. This
 * enum selects whether that integer is a 32-bit [INT] or a 64-bit [LONG].
 *
 * @property type The corresponding [DataType] for this flag storage type.
 */
enum class FlagType(val type: DataType) {
    INT(DataType.INT), LONG(DataType.LONG);

}