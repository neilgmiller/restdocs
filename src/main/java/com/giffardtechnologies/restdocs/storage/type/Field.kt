package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.Restriction

open class Field(
    val name: String,
    val longName: String,
    val description: String? = null,
    @JsonProperty("default")
    val defaultValue: String? = null,
    @JsonProperty("clientDefault")
    val defaultValueToSendFromClient: String? = null,
    @JsonProperty("required")
    val isRequired: Boolean = true,
    val sampleValues: List<String>? = null,
    type: DataType? = null,
    interpretedAs: BasicType? = null,
    @JsonProperty("typeref")
    typeRef: String? = null,
    key: KeyType? = null,
    flagType: FlagType? = null,
    items: TypeSpec? = null,
    restrictions: ArrayList<Restriction>? = null,
    fields: ArrayList<FieldListElement>? = null,
    values: ArrayList<EnumConstant>? = null,
) : TypeSpec(type, interpretedAs, typeRef, key, flagType, items, restrictions, fields, values), FieldListElement, Validatable {
    companion object {
        val alphaNumericRegex = Regex("^[A-Za-z][A-Za-z0-9_]*$")
    }
    override fun validate(validationContext: Any?) {
        super.validate(validationContext)
        if (name.isBlank()) {
            throw ValidationException("Field must have a name")
        }
        if (longName.isBlank()) {
            throw ValidationException("Field must have a long name")
        }
        if (!longName.matches(alphaNumericRegex)) {
            throw ValidationException("Field long name must be alphanumeric, and cannot start with a number: '$longName'")
        }
    }
}