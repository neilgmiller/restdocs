package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.TypeSpec

class Response : TypeSpec() {
    // TODO in the future added an encoding type option and way s to specify non-JSON responses
    val description: String? = null
    val hasDescription: Boolean
        get() = description != null && !description.isEmpty()
}
