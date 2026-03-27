package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.Context
import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.NamedBitSet
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.NamedType
import io.vavr.collection.Array

fun document(title: String, configure: DocumentConfiguration.() -> Unit): Document {
    val documentBuilder = DocumentBuilder(title)
    documentBuilder.configure()
    return documentBuilder.build()
}

class DelegatingContext(var context: Context) : Context {
    override fun getTypeByName(name: String): NamedType<*>? {
        return context.getTypeByName(name)
    }
}

open class DocumentConfiguration {

    open val context = DelegatingContext(AccumulatingContext())

    protected val bitSets: ArrayList<NamedBitSet> = ArrayList()
    protected val enumerations: ArrayList<NamedEnumeration> = ArrayList()
    protected val dataObjects: ArrayList<DataObject> = ArrayList()

    protected val dataObjectsByName: MutableMap<String, DataObject> = LinkedHashMap()
    protected val enumerationsByName: MutableMap<String, NamedEnumeration> = LinkedHashMap()
    protected val bitSetsByName: MutableMap<String, NamedBitSet> = LinkedHashMap()

    var service: Service? = null

    inner class AccumulatingContext : Context {
        override fun getTypeByName(name: String): NamedType<*>? {
            return dataObjectsByName[name] ?: enumerationsByName[name] ?: bitSetsByName[name]
        }
    }

    fun addNamedBitSet(namedBitSet: NamedBitSet) {
        bitSets.add(namedBitSet)
        bitSetsByName[namedBitSet.typeName] = namedBitSet
    }

    fun addNamedEnumeration(namedEnumeration: NamedEnumeration) {
        enumerations.add(namedEnumeration)
        enumerationsByName[namedEnumeration.typeName] = namedEnumeration
    }

    fun <T> enumeration(title: String, keyType: DataType.UsableAsKey<T>, configure: NamedEnumerationConfiguration<T>.() -> Unit) {
        val namedEnumeration = namedEnumeration(title, keyType, configure)
        enumerations.add(namedEnumeration)
        enumerationsByName[namedEnumeration.typeName] = namedEnumeration
    }

    fun addDataObject(dataObject: DataObject) {
        dataObjects.add(dataObject)
        dataObjectsByName[dataObject.typeName] = dataObject
    }

}

private class DocumentBuilder(private val title: String) : DocumentConfiguration() {

    fun build(): Document {
        val document = Document(
            title,
            Array.ofAll(bitSets),
            Array.ofAll(enumerations),
            Array.ofAll(dataObjects),
            service
        )
        context.context = document
        return document
    }

}