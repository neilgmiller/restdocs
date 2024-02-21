package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.storage.Document
import com.giffardtechnologies.restdocs.storage.type.BasicType
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.TypeSpec
import java.util.*

@Suppress("unused")
class PlainTextTool(document: Document) {
    private val dataObjectNames: MutableSet<String>

    init {
        val dataObjects = document.dataObjects
        dataObjectNames = HashSet(dataObjects.size * 2)
        for (dataObject in dataObjects) {
            dataObjectNames.add(dataObject.name)
        }
    }

    fun fieldClass(field: Field): String? {
        Objects.requireNonNull(
            field,
            "A field is required"
        )
        return getTypeString(field)
    }

    fun stringFor(type: BasicType): String {
        return type.toString().lowercase()
    }

    private fun getTypeString(typeSpec: TypeSpec): String? {
        val type = typeSpec.type
        if (type != null) {
            return when (type) {
                DataType.INT,
                DataType.LONG,
                DataType.FLOAT,
                DataType.DOUBLE,
                DataType.STRING,
                DataType.BOOLEAN,
                DataType.OBJECT,
                DataType.DATE -> type.toString().lowercase()
                DataType.COLLECTION -> "collection of ${typeSpec.key!!.type.toString().lowercase()} to ${
                    getTypeString(typeSpec.items!!)
                }"
                DataType.ENUM -> "enum keyed on ${typeSpec.key!!.type.toString().lowercase()}"
                DataType.ARRAY -> "array of ${getTypeString(typeSpec.items!!)}"
                DataType.BITSET -> "bitset in a ${typeSpec.flagType!!.type.toString().lowercase()}"
            }
        } else if (typeSpec.typeRef != null) {
            return typeSpec.typeRef
        }
        return null
    }
}
