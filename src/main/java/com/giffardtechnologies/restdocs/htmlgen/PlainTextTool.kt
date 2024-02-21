package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.type.BasicType
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import java.util.*

class PlainTextTool(private val mDocument: Document) {
    private val mDataObjectNames: MutableSet<String>

    init {
        val dataObjects = mDocument.dataObjects
        mDataObjectNames = HashSet(dataObjects.size * 2)
        for (dataObject in dataObjects) {
            mDataObjectNames.add(dataObject.name)
        }
    }

    fun fieldClass(field: Field): String? {
        Objects.requireNonNull(
            field,
            "A field is required"
        )
        return getTypeString(field, field.isRequired)
    }

    fun stringFor(type: BasicType): String {
        return type.toString().lowercase()
    }

    private fun getTypeString(typeSpec: TypeSpec, required: Boolean): String? {
        val type = typeSpec.type
        if (type != null) {
            return when (type) {
                DataType.INT, DataType.LONG, DataType.FLOAT, DataType.DOUBLE, DataType.STRING, DataType.BOOLEAN, DataType.OBJECT, DataType.DATE -> type.toString()
                    .lowercase()

                DataType.COLLECTION -> "collection of " + typeSpec.key.type.toString()
                    .lowercase() + " to " + getTypeString(typeSpec.items, false)

                DataType.ENUM -> "enum keyed on " + typeSpec.key.type.toString()
                    .lowercase()

                DataType.ARRAY ->                    // pass required false, since we can't use primitives
                    "array of " + getTypeString(typeSpec.items, false)

                DataType.BITSET -> "bitset in a " + typeSpec.flagType.type.toString()
                    .lowercase()
            }
        } else if (typeSpec.typeRef != null) {
            return typeSpec.typeRef
        }
        return null
    }
}
