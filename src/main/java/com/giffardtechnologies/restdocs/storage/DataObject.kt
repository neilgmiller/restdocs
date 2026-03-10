package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.validateHasNoDuplicates

/**
 * A named data object that defines a structured type with a list of fields.
 *
 * Data objects can form an inheritance hierarchy via [discriminator] and [childTypes], where the
 * discriminator field value determines which child type to use when deserializing polymorphic
 * payloads.
 *
 * @property name The unique identifier name of this data object, used for type references.
 * @property isHidden When `true`, this object is excluded from generated documentation output.
 * @property description Human-readable description of the purpose of this data object.
 * @property fields The list of fields (or field-include elements) that make up this object.
 * @property discriminator An optional field whose value distinguishes between [childTypes].
 * @property childTypes Optional list of sub-types that extend this object via the discriminator.
 * @property discriminatorValue The value of the parent's discriminator field that maps to this
 * child type, used when this object is itself a child type in an inheritance hierarchy.
 */
class DataObject(
    val name: String,
    val isHidden: Boolean = false,
    val description: String? = null,
    val fields: ArrayList<FieldListElement>,
    val discriminator: Field? = null,
    @JsonProperty("child types") val childTypes: ArrayList<DataObject>? = null,
    @JsonProperty("discriminator value") val discriminatorValue: String? = null,
) : Validatable {
    /**
     * Validates this data object, checking for a non-blank name and no duplicate field names.
     * During accumulation phase, also ensures the name is globally unique among all referencable
     * types.
     */
    override fun validate(validationContext: Any?) {
        if (name.isBlank()) {
            throw ValidationException("Data object must have a name")
        }
        if (validationContext is DocValidator.AccumulatingContext) {
            if (validationContext.referencableTypes.contains(name)) {
                throw ValidationException("A data object, enumeration, or bitset already exists with the name: \"$name\"")
            } else {
                fields.validateHasNoDuplicates()
                validationContext.referencableTypes.add(name)
            }
        } else {
            fields.validateHasNoDuplicates(validationContext.documentIfAvailable)
        }
    }

}