package com.giffardtechnologies.restdocs.storage.type

import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException

/**
 * @param value  The value of the key of this constant. This is a string regardless of the
 * [KeyType]
 * @param longName A human-readable name for this enum constant. Should be camel-case.
 * @param description A description of the semantics of this enum constant. There is no limit to the length, just what
 * is practical for the documentation.
 */
data class EnumConstant(
    val value: String,
    val longName: String? = null,
    val description: String? = null
) : Validatable {
    override fun validate(validationContext: Any?) {
        if ((longName ?: value).contains(Regex("[^a-zA-Z_0-9]"))) {
            throw ValidationException("Enum constant long name ('${longName ?: value}') contains invalid characters. (if `longName` was not provided `value` was used in it's place)")
        }
    }

}