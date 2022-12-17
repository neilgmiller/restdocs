package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty

class RequestBody {
    val description: String? = null

    @JsonProperty("content types")
    val contentTypes: ArrayList<String>? = null
}