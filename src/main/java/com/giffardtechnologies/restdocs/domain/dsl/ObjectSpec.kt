package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.FieldListElement
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun objectSpec(configure: ObjectSpecConfiguration.() -> Unit): TypeSpec.ObjectSpec {
    val objectBuilder = ObjectSpecBuilder()
    objectBuilder.configure()
    return objectBuilder.build()
}

open class ObjectSpecConfiguration protected constructor() {

    protected val fieldListElements: ArrayList<FieldListElement> = ArrayList()
    private val fieldsByName: MutableMap<String, Field> = LinkedHashMap()
    private val fieldsByLongName: MutableMap<String, Field> = LinkedHashMap()

    fun add(field: Field) {
        with(field) {
            val existingFieldByName = fieldsByName[name]
            val existingFieldByLongName = fieldsByLongName[longName]
            if (existingFieldByName != null) {
                throw IllegalArgumentException("Attempt to add fielded named `$name`, but value already exists $existingFieldByName")
            } else if (existingFieldByLongName != null) {
                throw IllegalArgumentException("Attempt to add fielded named `$longName`, but value already exists $existingFieldByLongName")
            } else {
                fieldListElements.add(field)
                fieldsByName[name] = field
                fieldsByLongName[longName] = field
            }
        }
    }

}

private class ObjectSpecBuilder : ObjectSpecConfiguration() {

    fun build(): TypeSpec.ObjectSpec {
        return TypeSpec.ObjectSpec(FieldElementList(Array.ofAll(fieldListElements)))
    }

}