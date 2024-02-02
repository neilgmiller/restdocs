package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.model.FieldPath
import com.giffardtechnologies.restdocs.model.FieldPathLeaf
import com.giffardtechnologies.restdocs.model.FieldPathSet
import com.giffardtechnologies.restdocs.model.FieldPathStem
import io.vavr.collection.Array
import io.vavr.collection.HashSet

/**
 * A class that manages a list of fields whether a straight list or a include
 */
class FieldElementList {
    private var fieldListElements: ArrayList<FieldListElement>? = ArrayList()

    @Transient
    private var fields: ArrayList<Field>? = null

    @Transient
    private var parentDocument: Document? = null

    @Transient
    private var parentType: NamedType? = null

    fun getFields(): ArrayList<Field>? {
        if (fields == null && fieldListElements != null) {
            val newFields = ArrayList<Field>()
            for (fieldListElement in fieldListElements!!) {
                if (fieldListElement is Field) {
                    newFields.add(fieldListElement)
                } else if (fieldListElement is FieldListIncludeElement) {
                    val includedObject = if (parentDocument == null) {
                        throw IllegalStateException("Cannot find '" + fieldListElement.include)
                    } else {
                        parentDocument!!.getDataObjectByName(fieldListElement.include)
                    }
                    if (fieldListElement.excluding.isEmpty()) {
                        newFields.addAll(includedObject.fields)
                    } else {
                        val fieldPaths = fieldListElement.excluding.map { FieldPath(it) }
                        val excludingPathSet = FieldPathSet.ofAll(fieldPaths)

                        val fields = includedObject.fields

                        val fieldsToAdd = getIncludedFields(fields, excludingPathSet, Array.empty())

                        newFields.addAll(fieldsToAdd)
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

    private fun getIncludedFields(
        fields: ArrayList<Field>,
        excludingPathSet: FieldPathSet,
        parentPath: Array<String>
    ): ArrayList<Field> {
        val includedFields = ArrayList<Field>()

        // validate first level fields
        val fieldNames = fields.stream().map { it.longName }.collect(HashSet.collector())
        val excludedFieldNames = HashSet.ofAll(excludingPathSet.map { it.field })
        if (!fieldNames.containsAll(excludedFieldNames)) {
            val missedExcludes = excludedFieldNames.removeAll(fieldNames)
            throw IllegalStateException(
                "'excluding' element refers to unknown field${if (missedExcludes.size() > 1) "s" else ""}: ${
                    missedExcludes.map { "'" + parentPath.joinToString(separator = ".", postfix = ".") + it + "'" }
                        .joinToString(
                            separator = ", "
                        )
                }"
            )
        }

        fields.forEach { field ->
            when(val node = excludingPathSet[field.longName]) {
                is FieldPathLeaf -> {}
                is FieldPathStem -> {
                    val newPath = parentPath.append(field.longName)
                    val subField = Field()
                    subField.name = field.name
                    subField.longName = field.longName
                    subField.parent = field.parent
                    subField.parentDocument = parentDocument
                    if (field.typeRef != null) {
                        val dataObject = parentDocument!!.getDataObjectByName(field.typeRef)
                        subField.type = DataType.OBJECT
                        subField.fields = getIncludedFields(dataObject.fields, node.childPathElements, newPath)
                    } else if (field.type == DataType.ARRAY && field.items!!.typeRef != null) {
                        val dataObject = parentDocument!!.getDataObjectByName(field.items.typeRef)
                        subField.type = DataType.ARRAY
                        subField.items = TypeSpec()
                        subField.items.parentDocument = parentDocument
                        subField.items.type = DataType.OBJECT
                        subField.items.fields = getIncludedFields(dataObject.fields, node.childPathElements, newPath)
                    } else if (field.type == DataType.OBJECT) {
                        subField.type = DataType.OBJECT
                        subField.fields = getIncludedFields(field.fields!!, node.childPathElements, newPath)
                    } else if (field.type == DataType.ARRAY && field.items!!.type == DataType.OBJECT) {
                        subField.type = DataType.ARRAY
                        subField.items = TypeSpec()
                        subField.items.parentDocument = parentDocument
                        subField.items.type = DataType.OBJECT
                        subField.items.fields = getIncludedFields(
                            field.items.fields!!,
                            node.childPathElements,
                            newPath
                        )
                    } else {
                        throw IllegalStateException(
                            "Cannot exclude sub-fields of non-object type (type-ref or object): '${
                                newPath.joinToString(
                                    separator = "."
                                )
                            }'"
                        )
                    }
                    includedFields.add(subField)
                }
                null -> includedFields.add(field)
            }
        }

        return includedFields
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
            field.parentDocument = parentDocument
        }
    }

    fun setParentDocument(parentDocument: Document?) {
        this.parentDocument = parentDocument
        fieldListElements?.forEach {
            when(it) {
                is Field -> it.parentDocument = parentDocument
            }
        }
    }

    fun setParentType(parentType: NamedType?) {
        this.parentType = parentType
    }
}
