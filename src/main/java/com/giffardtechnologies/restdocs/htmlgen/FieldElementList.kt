package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.model.FieldPath
import com.giffardtechnologies.restdocs.model.FieldPathLeaf
import com.giffardtechnologies.restdocs.model.FieldPathSet
import com.giffardtechnologies.restdocs.model.FieldPathStem
import com.giffardtechnologies.restdocs.storage.DataObject
import com.giffardtechnologies.restdocs.storage.Document
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.FieldListIncludeElement
import com.giffardtechnologies.restdocs.storage.type.NamedType
import com.giffardtechnologies.restdocs.storage.type.TypeSpec
import io.vavr.collection.Array
import io.vavr.collection.HashSet

/**
 * A class that manages a list of fields whether a straight list or a include
 */
class FieldElementList(
    private val parentDocument: Document,
    private val fieldListElements: ArrayList<FieldListElement>
) {

    private var fields: ArrayList<Field>? = null
    private var parentType: NamedType? = null

    private val dataObjectsByName = parentDocument.dataObjects.associateBy { it.name }

    private val DataObject.computedFields: ArrayList<Field>
        get() {
            return FieldElementList(parentDocument, this.fields).getFields()
        }

    private val TypeSpec.computedFields: ArrayList<Field>
        get() {
            return FieldElementList(parentDocument, this.fields!!).getFields()
        }

    fun getFields(): ArrayList<Field> {
        return fields ?: run {
            val newFields = ArrayList<Field>()
            for (fieldListElement in fieldListElements) {
                if (fieldListElement is Field) {
                    newFields.add(fieldListElement)
                } else if (fieldListElement is FieldListIncludeElement) {
                    val includedObject = dataObjectsByName[fieldListElement.include]
                        ?: throw IllegalStateException("Cannot find '" + fieldListElement.include)
                    if (fieldListElement.excluding.isEmpty()) {
                        newFields.addAll(includedObject.computedFields)
                    } else {
                        val fieldPaths = fieldListElement.excluding.map { FieldPath(it) }
                        val excludingPathSet = FieldPathSet.ofAll(fieldPaths)

                        val fields = includedObject.computedFields

                        newFields.addAll(getIncludedFields(fields, excludingPathSet, Array.empty()))
                    }
                } else {
                    throw IllegalStateException("Unsupported element type: " + fieldListElement.javaClass.name)
                }
            }
            fields = newFields
            newFields
        }
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
                    val subField = if (field.typeRef != null) {
                        val dataObject = dataObjectsByName[field.typeRef]
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.OBJECT,
                            fields = ArrayList(getIncludedFields(dataObject!!.computedFields, node.childPathElements, newPath))
                        )
                    } else if (field.type == DataType.ARRAY && field.items!!.typeRef != null) {
                        val dataObject = dataObjectsByName[field.items.typeRef]
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.ARRAY,
                            items = TypeSpec(
                                type = DataType.OBJECT,
                                fields = ArrayList(getIncludedFields(dataObject!!.computedFields, node.childPathElements, newPath))
                            )
                        )
                    } else if (field.type == DataType.OBJECT) {
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.OBJECT,
                            fields = ArrayList(getIncludedFields(field.computedFields, node.childPathElements, newPath)),
                        )
                    } else if (field.type == DataType.ARRAY && field.items!!.type == DataType.OBJECT) {
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.ARRAY,
                            items = TypeSpec(
                                type = DataType.OBJECT,
                                fields = ArrayList(
                                    getIncludedFields(
                                        field.items.computedFields,
                                        node.childPathElements,
                                        newPath
                                    )
                                )
                            )
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

    fun hasFields(): Boolean {
        return fieldListElements.isNotEmpty()
    }

    val hasFields: Boolean
        get() = fieldListElements.isNotEmpty()

    fun setParentType(parentType: NamedType?) {
        this.parentType = parentType
    }
}
