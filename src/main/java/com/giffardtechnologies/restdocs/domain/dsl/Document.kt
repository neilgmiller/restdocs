package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.Context
import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.DelegatingContext
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.type.DataType
import io.vavr.collection.Array
import io.vavr.collection.HashMap

fun document(title: String, configure: DocumentConfiguration.() -> Unit): Document {
    val context = DelegatingContext()
    val alterTable = DocumentBuilder(title, context)
    alterTable.configure()
    return alterTable.build()
}

open class DocumentConfiguration(open val context: Context) {

    protected val enumerations: ArrayList<NamedEnumeration> = ArrayList()
    protected val dataObjects: ArrayList<DataObject> = ArrayList()

    protected val dataObjectsByName: MutableMap<String, DataObject> = LinkedHashMap()
    protected val enumerationsByName: MutableMap<String, NamedEnumeration> = LinkedHashMap()

    var service: Service? = null

    fun addNamedEnumeration(namedEnumeration: NamedEnumeration) {
        enumerations.add(namedEnumeration)
        enumerationsByName[namedEnumeration.typeName] = namedEnumeration
    }

    fun <T> enumeration(title: String, keyType: DataType.BasicKey<T>, configure: NamedEnumerationConfiguration<T>.() -> Unit) {
        val namedEnumeration = namedEnumeration(title, keyType, configure)
        enumerations.add(namedEnumeration)
        enumerationsByName[namedEnumeration.typeName] = namedEnumeration
    }

    fun addDataObject(dataObject: DataObject) {
        dataObjects.add(dataObject)
        dataObjectsByName[dataObject.typeName] = dataObject
    }

}

private class DocumentBuilder(private val title: String, override val context: DelegatingContext) : DocumentConfiguration(context) {

    fun build(): Document {
        val document = Document(
            title,
            Array.ofAll(enumerations),
            Array.ofAll(dataObjects),
            HashMap.ofAll(dataObjectsByName),
            HashMap.ofAll(enumerationsByName),
            service
        )
        context.context = document
        return document
    }

}