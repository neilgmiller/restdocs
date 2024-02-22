package com.giffardtechnologies.restdocs.storage.type

/**
 * @param value  The value of the key of this constant. This is a string regardless of the
 * [KeyType]
 * @param longName A human-readable name for this enum constant. Should be camel-case.
 * @param description A description of the semantics of this enum constant. There is no limit to the length, just what
 * is practical for the documentation.
 */
data class EnumConstant(
    val value: String,
    val longName: String,
    val description: String? = null
)