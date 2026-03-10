package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.Restriction

/**
 * The base class for all type specifications in the REST documentation model.
 *
 * A `TypeSpec` describes the data type of a field, response, or other typed element. Every
 * instance must specify exactly one of [type] or [typeRef].
 *
 * @property type The explicit [DataType] of this element.
 * @property interpretedAs An optional [BasicType] describing how the raw [type] should be
 * semantically interpreted (e.g., an INT that is actually a BOOLEAN).
 * @property typeRef A reference to a named type (data object, enumeration, or bitset) defined
 * elsewhere in the document.
 * @property key The key type for [DataType.ENUM], [DataType.BITSET], and [DataType.COLLECTION].
 * @property flagType The integer storage type for [DataType.BITSET] fields.
 * @property items The element type for [DataType.ARRAY] and [DataType.COLLECTION].
 * @property restrictions Optional value restrictions (e.g., length or pattern constraints).
 * @property fields Inline field definitions when [type] is [DataType.OBJECT].
 * @property values Enumeration or bitset constants when [type] is [DataType.ENUM] or
 * [DataType.BITSET].
 */
open class TypeSpec(
    val type: DataType? = null,
    val interpretedAs: BasicType? = null,
    @JsonProperty("typeref")
    val typeRef: String? = null,
    val key: KeyType? = null,
    val flagType: FlagType? = null,
    val items: TypeSpec? = null,
    val restrictions: ArrayList<Restriction>? = null,
    val fields: ArrayList<FieldListElement>? = null,
    val values: ArrayList<EnumConstant>? = null,
) : Validatable {

    /**
     * Validates the type specification, enforcing rules such as:
     * - Exactly one of [type] or [typeRef] must be set.
     * - [DataType.ARRAY] requires [items].
     * - [DataType.OBJECT] requires [fields].
     * - [DataType.COLLECTION] requires both [key] and [items].
     * - [DataType.ENUM] requires a valid [key] and non-empty [values] with no duplicates.
     * - [DataType.BITSET] requires non-empty [values] where every constant has a long name.
     * - [typeRef] is validated against the known referencable types in a full validation context.
     */
    override fun validate(validationContext: Any?) {
        val classString = this::class.simpleName
        if (type != null && typeRef != null) {
            throw ValidationException("$classString cannot have both 'type' and 'typeref'")
        }
        if (type != null) {
            when(type) {
                DataType.INT,
                DataType.LONG,
                DataType.FLOAT,
                DataType.DOUBLE,
                DataType.STRING,
                DataType.BOOLEAN ,
                DataType.DATE -> {}
                DataType.ARRAY -> {
                    if (items == null) {
                        throw ValidationException("$classString of 'array' type must define 'items'")
                    }
                }
                DataType.OBJECT -> {
                    if (fields == null) {
                        throw ValidationException("$classString of 'object' type must define 'fields'")
                    }
                    fields.validateHasNoDuplicates(validationContext.documentIfAvailable)
                }
                DataType.COLLECTION -> {
                    if (key == null) {
                        throw ValidationException("$classString of 'collection' type must define 'key'")
                    }
                    if (items == null) {
                        throw ValidationException("$classString of 'collection' type must define 'items'")
                    }
                }
                DataType.ENUM -> {
                    val keyStringInvalid = when (key) {
                        KeyType.INT -> { it: String ->
                            try {
                                it.toInt()
                                false
                            } catch (e: Exception) {
                                true
                            }
                        }
                        KeyType.LONG -> { it: String ->
                            try {
                                it.toLong()
                                false
                            } catch (e: Exception) {
                                true
                            }
                        }
                        KeyType.STRING -> { _ -> false }
                        KeyType.ENUM -> throw ValidationException("$classString of 'enum' type cannot have a 'key' of type 'enum'")
                        null -> throw ValidationException("$classString of 'enum' type missing 'key' of type")
                    }
                    if (values == null) {
                        throw ValidationException("$classString of 'enum' type must define 'values'")
                    } else {
                        val valuesKeySet = mutableSetOf<String>()
                        val valuesNameSet = mutableSetOf<String>()
                        values.forEach {
                            if (keyStringInvalid(it.value)) {
                                throw ValidationException("Key '${it.value}' in '$classString' does not match key type: $key")
                            }
                            if (valuesKeySet.contains(it.value)) {
                                throw ValidationException("Duplicate key '${it.value}' found in '$classString'")
                            }
                            valuesKeySet.add(it.value)
                            if (valuesNameSet.contains(it.longName)) {
                                throw ValidationException("Duplicate longName '${it.longName}' found in '$classString'")
                            }
                            valuesNameSet.add(it.value)
                        }
                    }
                }
                DataType.BITSET -> {
                    if (values == null) {
                        throw ValidationException("$classString of 'bitset' type must define 'values'")
                    }
                    values.forEach {
                        if (it.longName == null) {
                            throw ValidationException("$classString of 'bitset' defines a flag constants without a long name.")
                        }
                    }
                }
            }
        } else if (typeRef != null) {
            if (validationContext is DocValidator.FullContext) {
                if (!validationContext.referencableTypes.contains(typeRef)) {
                    throw ValidationException("$classString's type reference refers to missing type: '$typeRef'")
                }
            }
        } else {
            throw ValidationException("$classString must have one of [type, typeref]")
        }
    }
}

