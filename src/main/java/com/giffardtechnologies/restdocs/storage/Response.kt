package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.type.BasicType
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.FlagType
import com.giffardtechnologies.restdocs.storage.type.KeyType
import com.giffardtechnologies.restdocs.storage.type.TypeSpec

// TODO in the future added an encoding type option and way s to specify non-JSON responses
class Response(
    val description: String? = null,
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
) : TypeSpec(type, interpretedAs, typeRef, key, flagType, items, restrictions, fields, values)
