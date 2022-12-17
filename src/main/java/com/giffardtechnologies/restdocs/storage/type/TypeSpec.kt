package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
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
)

