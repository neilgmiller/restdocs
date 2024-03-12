package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

data class Field(
    val name: String,
    val longName: String,
    val type: TypeSpec,
    val description: String? = null,
    val defaultValue: String? = null,
    val isRequired: Boolean = true,
    val sampleValues: Array<String> = Array.empty(),
    val parentName: String? = null,
) : FieldListElement
{
    val lazyContext by lazy { FieldContext(1) }

    var context: FieldContext = FieldContext(1)

    fun hasDefaultValue(): Boolean {
        return defaultValue != null
    }

    // for velocity templating
    val hasDefaultValue: Boolean
        get() = hasDefaultValue()

}

data class FieldContext(val dummy: Int?)