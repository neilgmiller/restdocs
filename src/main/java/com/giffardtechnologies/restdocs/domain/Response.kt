package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.TypeSpec

// TODO in the future added an encoding type option and way s to specify non-JSON responses
data class Response(
    val typeSpec: TypeSpec,
    val description: String? = null,
)
