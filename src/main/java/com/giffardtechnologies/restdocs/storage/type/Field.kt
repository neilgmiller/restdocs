package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.TrueOnNullBooleanDeserializer
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.Restriction
import tools.jackson.databind.annotation.JsonDeserialize

/**
 * A typed, named field that can appear in a data object, method parameters, headers, or an inline
 * object's field list.
 *
 * Extends [TypeSpec] with field-level metadata such as names, documentation, defaults, and whether
 * the field is required.
 *
 * @property name The short (wire) name of the field as it appears in the JSON payload.
 * @property longName A human-readable, alphanumeric name used for code generation. Must start with
 * a letter and contain only letters, digits, and underscores (or spaces if
 * [com.giffardtechnologies.restdocs.DocValidator.ValidationOptions.longNameAllowsSpaces] is set).
 * @property description Human-readable description of this field's purpose.
 * @property defaultValue The server-side default value for this field when it is absent in a
 * request, expressed as a string regardless of the underlying type.
 * @property defaultValueToSendFromClient The value that client code should send when the user has
 * not explicitly set this field.
 * @property isRequired Whether clients must include this field. Defaults to `true` when absent in
 * the source document (handled by [TrueOnNullBooleanDeserializer]).
 * @property sampleValues Optional example values shown in generated documentation.
 * @param type The explicit [DataType] of this field.
 * @param parsedAs Used only when [type] is [DataType.STRING]; denotes the type the string should
 * be parsed to. Can be combined with [interpretedAs].
 * @param interpretedAs An optional [BasicType] describing a semantic re-interpretation of [type].
 * @param typeRef A reference to a named type defined elsewhere in the document.
 * @param key The key type for collection or enum fields.
 * @param flagType The integer storage type for bitset fields.
 * @param items The element type for array or collection fields.
 * @param restrictions Optional value restrictions.
 * @param fields Inline field definitions when the field type is [DataType.OBJECT].
 * @param values Enumeration or bitset constants when the field type is [DataType.ENUM] or
 * [DataType.BITSET].
 */
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
    parsedAs: BasicType? = null,
    interpretedAs: BasicType? = null,
    @JsonProperty("typeref")
    typeRef: String? = null,
    key: KeyType? = null,
    flagType: FlagType? = null,
    items: TypeSpec? = null,
    restrictions: ArrayList<Restriction>? = null,
    fields: ArrayList<FieldListElement>? = null,
    values: ArrayList<EnumConstant>? = null,
) : TypeSpec(type, parsedAs, interpretedAs, typeRef, key, flagType, items, restrictions, fields, values), FieldListElement, Validatable {

    companion object {
        /** Regex for validating a [Field.longName] that does not allow spaces. */
        val alphaNumericRegex = Regex("^[A-Za-z][A-Za-z0-9_]*$")

        /** Regex for validating a [Field.longName] that allows internal spaces. */
        val alphaNumericWithSpacesRegex = Regex("^[A-Za-z][A-Za-z0-9_ ]*$")

        /**
         * Returns the appropriate long-name validation regex based on whether the current
         * validation context permits spaces in long names.
         */
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

    /**
     * Validates this field, checking that [name] and [longName] are non-blank and that [longName]
     * matches the required alphanumeric pattern. Long name validation is skipped when
     * [com.giffardtechnologies.restdocs.DocValidator.ValidationOptions.longNameIsOptional] is set
     * and the long name is blank.
     */
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

    /**
     * Returns a copy of this field with [isRequired] overridden to the given value. All other
     * properties are preserved unchanged.
     */
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
            parsedAs = this.parsedAs,
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