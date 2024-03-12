package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.NamedType
import io.vavr.collection.Array
import io.vavr.collection.HashMap

interface Context {
    fun getTypeByName(name: String) : NamedType<*>?
}

class Document(
    var title: String,
    val enumerations: Array<NamedEnumeration>,
    val dataObjects: Array<DataObject>,
    val service: Service? = null,
) : Context {

    private val dataObjectNames = HashMap.ofAll(dataObjects.associateBy { it.typeName })
    private val enumerationNames = HashMap.ofAll(enumerations.associateBy { it.typeName })

    override fun getTypeByName(name: String): NamedType<*>? {
        val dataObject = dataObjectNames[name].orNull
        val namedEnumeration = enumerationNames[name].orNull
        check(!(dataObject == null && namedEnumeration == null)) { "Type reference to undefined type: $name." }
        check(!(dataObject != null && namedEnumeration != null)) { "Ambiguous type reference: $name." }
        return dataObject ?: namedEnumeration
    }
}
