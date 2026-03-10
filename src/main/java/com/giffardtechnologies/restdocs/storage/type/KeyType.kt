package com.giffardtechnologies.restdocs.storage.type

/**
 * The underlying data type used as the key (discriminator value) for an [DataType.ENUM],
 * [DataType.BITSET], or [DataType.COLLECTION].
 *
 * @property type The corresponding [DataType] for this key type.
 */
enum class KeyType(val type: DataType) {
    INT(DataType.INT), LONG(DataType.LONG), STRING(DataType.STRING), ENUM(DataType.ENUM);
}