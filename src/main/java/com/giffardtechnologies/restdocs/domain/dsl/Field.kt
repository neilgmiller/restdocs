package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun field(name: String, longName: String, type: TypeSpec, configure: FieldConfiguration.() -> Unit): Field {
    val namedEnumerationBuilder = FieldBuilder(name, longName, type)
    namedEnumerationBuilder.configure()
    return namedEnumerationBuilder.build()
}

open class FieldConfiguration protected constructor() {

    var description: String? = null
    var defaultValue: String? = null
    var isRequired: Boolean = true

    protected val sampleValues: ArrayList<String> = ArrayList()

    fun sampleValue(value: String) {
        sampleValues.add(value)
    }

}

private class FieldBuilder(private val name: String, private val longName: String, private val type: TypeSpec) : FieldConfiguration() {

    fun build(): Field {
        return Field(
            name = name,
            longName = longName,
            type = type,
            description = description,
            defaultValue = defaultValue,
            isRequired = isRequired,
            sampleValues = Array.ofAll(sampleValues),
        )
    }

}