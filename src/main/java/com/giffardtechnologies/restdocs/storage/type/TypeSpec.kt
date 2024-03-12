package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.Restriction

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
                    if (key == KeyType.ENUM) {
                        throw ValidationException("$classString of 'enum' type cannot have a 'key' of type 'enum'")
                    }
                    if (values == null) {
                        throw ValidationException("$classString of 'enum' type must define 'values'")
                    }
                }
                DataType.BITSET -> {
                    if (values == null) {
                        throw ValidationException("$classString of 'bitset' type must define 'values'")
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

