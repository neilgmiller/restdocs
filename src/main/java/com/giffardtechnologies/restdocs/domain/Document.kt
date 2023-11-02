package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.NamedType
import io.vavr.collection.Array
import java.util.stream.Collectors

// Most of these methods are used by velocity
@Suppress("unused")
class Document private constructor(
    var title: String,
    val enumerations: Array<NamedEnumeration>,
    val dataObjects: Array<DataObject>,
    private var mDataObjectNames: io.vavr.collection.HashMap<String, DataObject>,
    private var mEnumerationNames: io.vavr.collection.HashMap<String, NamedEnumeration>,
    val service: Service? = null,
) {

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
                    io.vavr.collection.HashMap.ofAll(dataObjectsByName),
                    io.vavr.collection.HashMap.ofAll(enumerationsByName),
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

    fun getTypeByName(name: String): NamedType<*>? {
        val dataObject = mDataObjectNames[name].orNull
        val namedEnumeration = mEnumerationNames[name].orNull
        check(!(dataObject == null && namedEnumeration == null)) { "Type reference to undefined type: $name." }
        check(!(dataObject != null && namedEnumeration != null)) { "Ambiguous type reference: $name." }
        return dataObject ?: namedEnumeration
    }
}
