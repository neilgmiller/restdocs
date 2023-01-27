@file:JvmName("JacksonMapperBuilder")

package com.giffardtechnologies.restdocs.jackson

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.giffardtechnologies.restdocs.jackson.validation.ValidationModule

fun createMapper(validationContext: Any? = null): YAMLMapper {
    return YAMLMapper.builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .addModule(kotlinModule())
        .addModule(ValidationModule(validationContext))
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()
}