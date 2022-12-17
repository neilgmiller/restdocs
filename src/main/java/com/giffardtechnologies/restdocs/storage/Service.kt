package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.type.Field

data class Service(
    val description: String? = null,
    @JsonProperty("base path") val basePath: String? = null,
    val common: Common? = null,
    val methods: ArrayList<Method>? = null
)

class Common(
    val headers: ArrayList<Field>? = null,
    val parameters: ArrayList<Field>? = null,
    @JsonProperty("response objects") val responseDataObjects: ArrayList<DataObject>? = ArrayList(),
    val enums: ArrayList<NamedEnumeration>? = null,
)