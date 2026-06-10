package com.giffardtechnologies.restdocs.storage

import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.KeyType
import com.giffardtechnologies.restdocs.storage.type.NamedType
import com.giffardtechnologies.restdocs.storage.type.TypeSpec

/**
 * A top-level named enumeration that can be referenced by other fields in the document.
 *
 * @property name The unique identifier for this enumeration, used for type references.
 * @property description Human-readable description of the enumeration's purpose.
 * @param key The underlying key type used to represent enum values (e.g., integer or string).
 * @param values The list of valid enumeration constants.
 */
class NamedEnumeration(
    val name: String,
    val description: String? = null,
    key: KeyType,
    values: ArrayList<EnumConstant>,
) : TypeSpec(type = DataType.ENUM, null, key = key, values = values), NamedType, Validatable {

    override val typeName: String
        get() = name

    /**
     * Validates this enumeration, checking for a non-blank name and valid enum constant values.
     * During accumulation phase, also ensures the name is globally unique among all referencable
     * types.
     */
    override fun validate(validationContext: Any?) {
        super<TypeSpec>.validate(validationContext)
        if (name.isBlank()) {
            throw ValidationException("Enumeration must have a name")
        }
        if (validationContext is DocValidator.AccumulatingContext) {
            if (validationContext.referencableTypes.contains(name)) {
                throw ValidationException("A data object, enumeration, or bitset already exists with the name: \"$name\"")
            } else {
                validationContext.referencableTypes.add(name)
            }
        }
    }

}