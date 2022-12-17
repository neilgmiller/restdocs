package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty

data class Document(
        val title: String,
        val service: Service?,
        val enumerations: ArrayList<NamedEnumeration> = ArrayList(),
        @JsonProperty("data objects") val dataObjects: ArrayList<DataObject> = ArrayList()
)