package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.NamedType
import java.util.stream.Collectors
import kotlin.Boolean
import kotlin.IllegalStateException

/**
 * A class that manages a list of fields whether a straight list or a include
 */
class FieldElementList {
    private var fieldListElements: ArrayList<FieldListElement>? = ArrayList()

    @Transient
    private var fields: ArrayList<Field>? = null

    @Transient
    private lateinit var parentDocument: Document

    @Transient
    private var parentType: NamedType? = null

    fun getFields(): ArrayList<Field>? {
        if (fields == null && fieldListElements != null) {
            val newFields = ArrayList<Field>()
            for (fieldListElement in fieldListElements!!) {
                if (fieldListElement is Field) {
                    newFields.add(fieldListElement)
                } else if (fieldListElement is FieldListIncludeElement) {
                    val includedObject = parentDocument.getDataObjectByName(fieldListElement.include)
                        ?: throw IllegalStateException("Cannot find '" + fieldListElement.include)
                    if (fieldListElement.excluding.isEmpty()) {
                        newFields.addAll(includedObject.fields)
                    } else {
                        val fieldNameSet = includedObject.fields
                            .stream()
                            .map { obj: Field -> obj.longName }
                            .collect(Collectors.toSet())
                        if (!fieldNameSet.containsAll(fieldListElement.excluding)) {
                            val nonMatching = HashSet(fieldListElement.excluding)
                            nonMatching.removeAll(fieldNameSet)
                            throw IllegalStateException(
                                "Cannot find excluded field(s): " + nonMatching.joinToString(", ")
                            )
                        }
                        val fieldsToAdd = includedObject.fields
                            .stream()
                            .filter { field: Field ->
                                !fieldListElement.excluding
                                    .contains(field.longName)
                            }
                            .collect(Collectors.toList())
                        fields!!.addAll(fieldsToAdd)
                    }
                } else {
                    throw IllegalStateException("Unsupported element type: " + fieldListElement.javaClass.name)
                }
            }
            linkFields(newFields)
            fields = newFields
        }
        return fields
    }

    fun setFields(fields: ArrayList<Field>) {
        this.fields = ArrayList(fields)
        fieldListElements = ArrayList(fields)
        linkFields(fields)
    }

    fun hasFields(): Boolean {
        return fieldListElements != null && fieldListElements!!.isNotEmpty()
    }

    val hasFields: Boolean
        get() = fieldListElements != null && fieldListElements!!.isNotEmpty()

    fun getFieldListElements(): ArrayList<FieldListElement>? {
        return fieldListElements
    }

    fun setFieldListElements(fieldListElements: ArrayList<FieldListElement>) {
        this.fieldListElements = ArrayList(fieldListElements)
        fields = null
    }

    private fun linkFields(fields: ArrayList<Field>) {
        for (field in fields) {
            field.parent = parentType
            field.setParentDocument(parentDocument)
        }
    }

    fun setParentDocument(parentDocument: Document) {
        this.parentDocument = parentDocument
    }

    fun setParentType(parentType: NamedType) {
        this.parentType = parentType
    }
}
