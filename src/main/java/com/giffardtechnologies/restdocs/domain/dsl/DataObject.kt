package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun dataObject(name: String, configure: DataObjectConfiguration.() -> Unit): DataObject {
    val namedEnumerationBuilder = DataObjectBuilder(name)
    namedEnumerationBuilder.configure()
    return namedEnumerationBuilder.build()
}
// TODO make builders work with passing in a configuration

open class DataObjectConfiguration protected constructor() : ObjectSpecConfiguration() {

    var description: String? = null
    var isHidden: Boolean = false

}

private class DataObjectBuilder(private val name: String) : DataObjectConfiguration() {

    fun build(): DataObject {
        return DataObject(
            name,
            type = TypeSpec.ObjectSpec(FieldElementList(Array.ofAll(fieldListElements))),
            description,
            isHidden,
        )
    }

}