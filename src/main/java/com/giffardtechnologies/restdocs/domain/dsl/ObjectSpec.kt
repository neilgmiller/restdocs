package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.FieldListElement
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun objectSpec(configure: ObjectSpecConfiguration.() -> Unit): TypeSpec.ObjectSpec {
    val namedEnumerationBuilder = ObjectSpecBuilder()
    namedEnumerationBuilder.configure()
    return namedEnumerationBuilder.build()
}

open class ObjectSpecConfiguration protected constructor() {

    protected val fieldListElements: ArrayList<FieldListElement> = ArrayList()
    private val fieldsByName: MutableMap<String, Field> = LinkedHashMap()
    private val fieldsByLongName: MutableMap<String, Field> = LinkedHashMap()

    fun field(name: String, longName: String, type: TypeSpec, configure: FieldConfiguration.() -> Unit) {
        val existingFieldByName = fieldsByName[name]
        val existingFieldByLongName = fieldsByLongName[longName]
        if (existingFieldByName != null) {
            throw IllegalArgumentException("Attempt to add fielded named `$name`, but value already exists $existingFieldByName")
        } else if (existingFieldByLongName != null) {
            throw IllegalArgumentException("Attempt to add fielded named `$longName`, but value already exists $existingFieldByLongName")
        } else {
            val field = com.giffardtechnologies.restdocs.domain.dsl.field(name, longName, type, configure)
            fieldListElements.add(field)
            fieldsByName[name] = field
            fieldsByLongName[longName] = field
        }
    }

}

private class ObjectSpecBuilder : ObjectSpecConfiguration() {

    fun build(): TypeSpec.ObjectSpec {
        return TypeSpec.ObjectSpec(FieldElementList(Array.ofAll(fieldListElements)))
    }

}