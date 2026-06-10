@file:JvmName("JacksonMapperBuilder")

package com.giffardtechnologies.restdocs.jackson

import tools.jackson.databind.MapperFeature
import tools.jackson.databind.module.SimpleModule
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.module.kotlin.kotlinModule
import com.giffardtechnologies.restdocs.jackson.validation.ValidationModule

fun createMapper(validationContext: Any? = null, warningEmitter: (String) -> Unit = {}): YAMLMapper {
    val booleanModule = SimpleModule().addDeserializer(Boolean::class.java, YesNoBooleanDeserializer())
    return YAMLMapper.builder()
        .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
        .addModule(kotlinModule())
        .addModule(booleanModule)
        .addModule(ValidationModule(validationContext, warningEmitter))
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()
}