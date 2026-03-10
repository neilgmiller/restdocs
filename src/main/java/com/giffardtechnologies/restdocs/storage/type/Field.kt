package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.TrueOnNullBooleanDeserializer
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.Restriction
import tools.jackson.databind.annotation.JsonDeserialize

open class Field(
    val name: String,
    val longName: String = "",
    val description: String? = null,
    @JsonProperty("default")
    val defaultValue: String? = null,
    @JsonProperty("clientDefault")
    val defaultValueToSendFromClient: String? = null,
    @JsonProperty("required")
    @JsonDeserialize(using = TrueOnNullBooleanDeserializer::class)
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
        val alphaNumericWithSpacesRegex = Regex("^[A-Za-z][A-Za-z0-9_ ]*$")

        private fun getLongNameValidationRegex(validationContext: Any?): Regex {
            val longNameAllowsSpaces = if (validationContext is DocValidator.ValidationContext) {
                validationContext.validationOptions.longNameAllowsSpaces
            } else {
                false
            }
            return if (longNameAllowsSpaces) {
                alphaNumericWithSpacesRegex
            } else {
                alphaNumericRegex
            }
        }
    }

    override fun validate(validationContext: Any?) {
        super.validate(validationContext)
        if (name.isBlank()) {
            throw ValidationException("Field must have a name")
        }
        if (longName.isBlank()) {
            if (validationContext is DocValidator.ValidationContext && validationContext.validationOptions.longNameIsOptional) {
                return
            } else {
                throw ValidationException("Field must have a long name")
            }
        }
        if (!longName.matches(getLongNameValidationRegex(validationContext))) {
            throw ValidationException("Field long name must be alphanumeric, and cannot start with a number: '$longName'")
        }
    }

    fun copy(isRequired: Boolean): Field {
        return Field(
            name = this.name,
            longName = this.longName,
            description = this.description,
            defaultValue = this.defaultValue,
            defaultValueToSendFromClient = this.defaultValueToSendFromClient,
            isRequired = isRequired,
            sampleValues = this.sampleValues,
            type = this.type,
            interpretedAs = this.interpretedAs,
            typeRef = this.typeRef,
            key = this.key,
            flagType = this.flagType,
            items = this.items,
            restrictions = this.restrictions,
            fields = this.fields,
            values = this.values
        )
    }
}