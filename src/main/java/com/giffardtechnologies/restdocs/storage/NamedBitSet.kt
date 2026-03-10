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
 * A top-level named bitset that can be referenced by other fields in the document.
 *
 * A bitset is an enumeration where each constant represents a single bit flag and multiple flags
 * can be combined. All constants must have a [EnumConstant.longName].
 *
 * @property name The unique identifier for this bitset, used for type references.
 * @property description Human-readable description of the bitset's purpose.
 * @param key The underlying integer key type used to store the combined flag value.
 * @param values The list of individual flag constants defined for this bitset.
 */
class NamedBitSet(
    val name: String,
    val description: String? = null,
    key: KeyType,
    values: ArrayList<EnumConstant>,
) : TypeSpec(type = DataType.BITSET, null, key = key, values = values), NamedType, Validatable {

    override val typeName: String
        get() = name

    /**
     * Validates this bitset, checking for a non-blank name and that all flag constants have a
     * long name. During accumulation phase, also ensures the name is globally unique among all
     * referencable types.
     */
    override fun validate(validationContext: Any?) {
        super.validate(validationContext)
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