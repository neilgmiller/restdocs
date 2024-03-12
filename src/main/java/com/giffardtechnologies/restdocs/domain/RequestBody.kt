package com.giffardtechnologies.restdocs.domain

import io.vavr.collection.Array

data class RequestBody(
    val description: String? = null,
    var contentTypes: Array<String>? = null,
)
