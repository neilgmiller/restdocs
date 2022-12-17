package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement

class DataObject(
    val name: String,
    val isHidden: Boolean = false,
    val description: String? = null,
    val fields: ArrayList<FieldListElement>,
    val discriminator: Field? = null,
    @JsonProperty("child types") val childTypes: ArrayList<DataObject>? = null,
    @JsonProperty("discriminator value") val discriminatorValue: String? = null,
)