package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.Restriction

open class Field(
    val name: String,
    val longName: String,
    val description: String? = null,
    @JsonProperty("default")
    val defaultValue: String? = null,
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
) : TypeSpec(type, interpretedAs, typeRef, key, flagType, items, restrictions, fields, values), FieldListElement