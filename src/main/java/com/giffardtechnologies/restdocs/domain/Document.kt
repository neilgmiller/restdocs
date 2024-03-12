package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.NamedType
import io.vavr.collection.Array
import io.vavr.collection.HashMap

interface Context {
    fun getTypeByName(name: String) : NamedType<*>?
}

class DummyContext : Context {
    override fun getTypeByName(name: String): NamedType<*>? {
        throw UnsupportedOperationException("DummyContext does not support lookup.")
    }
}
class DelegatingContext(var context: Context) : Context {
    override fun getTypeByName(name: String): NamedType<*>? {
        return context.getTypeByName(name)
    }
}

// Most of these methods are used by velocity
@Suppress("unused")
class Document(
    var title: String,
    val enumerations: Array<NamedEnumeration>,
    val dataObjects: Array<DataObject>,
    private var mDataObjectNames: HashMap<String, DataObject>,
    private var mEnumerationNames: HashMap<String, NamedEnumeration>,
    val service: Service? = null,
) : Context {

    companion object {
        fun createDocument(title: String, configure: DocumentConfiguration.() -> Unit): Document {
            val alterTable = DocumentBuilder(title)
            alterTable.configure()
            return alterTable.build()

        }

        open class DocumentConfiguration {

            protected val enumerations: ArrayList<NamedEnumeration> = ArrayList()
            protected val dataObjects: ArrayList<DataObject> = ArrayList()

            protected val dataObjectsByName: MutableMap<String, DataObject> = LinkedHashMap()
            protected val enumerationsByName: MutableMap<String, NamedEnumeration> = LinkedHashMap()

            var service: Service? = null

            fun addNamedEnumeration(namedEnumeration: NamedEnumeration) {
                enumerations.add(namedEnumeration)
                enumerationsByName[namedEnumeration.typeName] = namedEnumeration
            }

            fun addDataObject(dataObject: DataObject) {
                dataObjects.add(dataObject)
                dataObjectsByName[dataObject.typeName] = dataObject
            }

        }

        class DocumentBuilder(private val title: String) : DocumentConfiguration() {

            fun build(): Document {
                return Document(
                    title,
                    Array.ofAll(enumerations),
                    Array.ofAll(dataObjects),
                    HashMap.ofAll(dataObjectsByName),
                    HashMap.ofAll(enumerationsByName),
                    service
                )
            }

        }

    }

    private var visibleDataObjects: Array<DataObject>? = null

    val hasDataObjects: Boolean
        get() = !dataObjects.isEmpty

    fun getVisibleDataObjects(): Array<DataObject> {
        val localVisibleDataObjects = visibleDataObjects ?: dataObjects.filter { !it.isHidden }

        visibleDataObjects = localVisibleDataObjects

        return localVisibleDataObjects
    }

    val hasVisibleDataObjects: Boolean
        get() = !getVisibleDataObjects().isEmpty

    fun getDataObjectByName(name: String): DataObject? {
        return mDataObjectNames[name].orNull
    }

    val hasEnumerations: Boolean
        get() = !enumerations.isEmpty

    fun getEnumerationByName(name: String?): NamedEnumeration? {
        return mEnumerationNames[name].orNull
    }

    override fun getTypeByName(name: String): NamedType<*>? {
        val dataObject = mDataObjectNames[name].orNull
        val namedEnumeration = mEnumerationNames[name].orNull
        check(!(dataObject == null && namedEnumeration == null)) { "Type reference to undefined type: $name." }
        check(!(dataObject != null && namedEnumeration != null)) { "Ambiguous type reference: $name." }
        return dataObject ?: namedEnumeration
    }
}
