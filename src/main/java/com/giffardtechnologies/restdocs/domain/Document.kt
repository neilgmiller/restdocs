package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.google.gson.annotations.SerializedName
import io.vavr.Tuple2
import io.vavr.collection.Array
import io.vavr.collection.Map
import java.util.stream.Collectors

// Most of these methods are used by velocity
@Suppress("unused")
class Document {

    companion object {
        fun createDocument(title: String, configure: DocumentConfiguration.() -> Unit) : Document {
            val alterTable = DocumentBuilder(title)
            alterTable.configure()
            return alterTable.build()

        }
    }

    open class DocumentConfiguration {

        private val enumerations: ArrayList<NamedEnumeration> = ArrayList()
        private val dataObjects: ArrayList<DataObject> = ArrayList()

        private var mDataObjectNames: MutableMap<String, DataObject> = LinkedHashMap()
        private var mEnumerationNames: MutableMap<String, NamedEnumeration> = LinkedHashMap()

        var service: Service? = null

        fun addNamedEnumeration() {

        }

        fun addDataObject() {

        }

    }

    class DocumentBuilder(val title: String) : DocumentConfiguration() {

        fun build(): Document {
            return Document()
        }

    }

    var title: String? = null
    @JvmField
    val enumerations: ArrayList<NamedEnumeration>? = ArrayList()

    @SerializedName("data objects")
    private val dataObjects: ArrayList<DataObject>? = ArrayList()

    @Transient
    private var visibleDataObjects: List<DataObject>? = null
    @JvmField
    var service: Service? = null
    private var mDataObjectNames: MutableMap<String?, DataObject>? = null
    private var mEnumerationNames: MutableMap<String?, NamedEnumeration>? = null
    fun buildMappings() {
        mDataObjectNames = HashMap(
            dataObjects!!.size * 2
        )
        for (dataObject in dataObjects) {
            mDataObjectNames[dataObject.name] = dataObject
            dataObject.setParent(this)
        }
        mEnumerationNames = HashMap(
            enumerations!!.size * 2
        )
        for (enumeration in enumerations) {
            mEnumerationNames[enumeration.name] = enumeration
        }
        if (service != null) {
            service.setParentDocument(this)
        }
    }

    fun hasDataObjects(): Boolean {
        return dataObjects != null && !dataObjects.isEmpty()
    }

    val hasDataObjects: Boolean
        get() = hasDataObjects()

    fun getDataObjects(): ArrayList<DataObject>? {
        return dataObjects
    }

    fun setDataObjects(dataObjects: ArrayList<DataObject>?) {
        visibleDataObjects = null
        this.dataObjects!!.clear()
        this.dataObjects.addAll(dataObjects!!)
    }

    fun getVisibleDataObjects(): List<DataObject>? {
        if (visibleDataObjects == null) {
            visibleDataObjects = dataObjects!!.stream().filter { o: DataObject -> !o.isHidden }
                .collect(Collectors.toList())
        }
        return visibleDataObjects
    }

    fun hasVisibleDataObjects(): Boolean {
        return !getVisibleDataObjects()!!.isEmpty()
    }

    val hasVisibleDataObjects: Boolean
        get() = hasVisibleDataObjects()

    fun getDataObjectByName(name: String?): DataObject? {
        return mDataObjectNames!![name]
    }

    fun hasEnumerations(): Boolean {
        return enumerations != null && !enumerations.isEmpty()
    }

    val hasEnumerations: Boolean
        get() = hasDataObjects()

    fun getEnumerationByName(name: String?): NamedEnumeration? {
        return mEnumerationNames!![name]
    }

    fun getTypeByName(name: String): NamedType? {
        val dataObject = mDataObjectNames!![name]
        val namedEnumeration = mEnumerationNames!![name]
        check(!(dataObject == null && namedEnumeration == null)) { "Type reference to undefined type: $name." }
        check(!(dataObject != null && namedEnumeration != null)) { "Ambiguous type reference: $name." }
        return dataObject ?: namedEnumeration
    }
}
