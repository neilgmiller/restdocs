package com.giffardtechnologies.restdocs.storage.type

import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException

/**
 * A single constant within an [DataType.ENUM] or [DataType.BITSET] type specification.
 *
 * @param value The key value of this constant. Stored as a string regardless of the [KeyType];
 * the parser validates that it is compatible with the declared key type.
 * @param longName A human-readable, alphanumeric name for this constant used in code generation.
 * Should be camelCase. When omitted, [value] is used in its place for validation purposes.
 * @param description A description of the semantics of this constant. Length is not restricted
 * beyond what is practical for generated documentation.
 */
data class EnumConstant(
    val value: String,
    val longName: String? = null,
    val description: String? = null
) : Validatable {
    /**
     * Validates that the effective name (i.e., [longName] if present, otherwise [value]) contains
     * only alphanumeric characters and underscores.
     */
    override fun validate(validationContext: Any?) {
        if ((longName ?: value).contains(Regex("[^a-zA-Z_0-9]"))) {
            throw ValidationException("Enum constant long name ('${longName ?: value}') contains invalid characters. (if `longName` was not provided `value` was used in it's place)")
        }
    }

}