package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.type.TypeSpec

class ContextualTypeSpec<T: TypeSpec>(
    val typeSpec: T,
    val path: List<String>,
)