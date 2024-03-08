package com.giffardtechnologies.restdocs.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec

data class ClassDefinition(val className: ClassName, val typeSpec: TypeSpec)