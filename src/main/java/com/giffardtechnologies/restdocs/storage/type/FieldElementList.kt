package com.giffardtechnologies.restdocs.storage.type

import com.giffardtechnologies.restdocs.model.FieldPathLeaf
import com.giffardtechnologies.restdocs.model.FieldPathSet
import com.giffardtechnologies.restdocs.model.FieldPathStem
import com.giffardtechnologies.restdocs.storage.DataObject
import com.giffardtechnologies.restdocs.storage.Document
import io.vavr.collection.Array
import io.vavr.collection.HashSet

/**
 * Pairs a resolved [Field] with the [FieldListIncludeElement] that brought it in, if any.
 *
 * @property field The resolved field.
 * @property includedBy The include element that contributed this field, or `null` if the field
 * was declared directly in the containing object.
 */
data class FieldDetails(val field: Field, val includedBy: FieldListIncludeElement? = null)

/**
 * Resolves and manages a list of [FieldListElement] items into a flat list of [Field] instances.
 *
 * Handles both direct [Field] elements and [FieldListIncludeElement] entries, expanding the latter
 * by inlining the referenced data object's fields (with exclusions and required-state overrides
 * applied).
 *
 * @constructor
 * @param parentDocument The document used to look up data objects referenced by include elements.
 * @param fieldListElements The raw field list to resolve.
 */
class FieldElementList(
    private val parentDocument: Document,
    private val fieldListElements: List<FieldListElement>
) {

    private var fields: ArrayList<Field>? = null
    private var fieldDetails: ArrayList<FieldDetails>? = null
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

    /**
     * Returns the resolved flat list of [Field] instances after expanding all include elements.
     * The result is cached after the first call.
     */
    fun getFields(): ArrayList<Field> {
        return fields ?: run {
            val newFields = ArrayList<Field>()
            for (fieldListElement in fieldListElements) {
                if (fieldListElement is Field) {
                    newFields.add(fieldListElement)
                } else if (fieldListElement is FieldListIncludeElement) {
                    val includedObject = dataObjectsByName[fieldListElement.include]
                        ?: throw IllegalStateException("Cannot find '" + fieldListElement.include)
                    val baseFields = if (fieldListElement.includeOnly.isNotEmpty()) {
                        getIncludedOnlyFields(
                            includedObject.computedFields,
                            fieldListElement.includeOnlyPathSet(),
                            Array.empty()
                        )
                    } else {
                        includedObject.computedFields
                    }
                    val includedFields = if (fieldListElement.excluding.isNotEmpty()) {
                        getIncludedFields(baseFields, fieldListElement.excludingPathSet(), Array.empty())
                    } else {
                        baseFields
                    }
                    val overrideRequired = fieldListElement.overrideRequired
                    val overriddenFields = if (overrideRequired == null) {
                        includedFields
                    } else {
                        val overrideExcluding = HashSet.ofAll(overrideRequired.excluding)
                        includedFields.map { field ->
                            if (overrideExcluding.contains(field.longName)) {
                                field
                            } else {
                                field.copy(isRequired = overrideRequired.required)
                            }
                        }
                    }
                    newFields.addAll(overriddenFields)
                } else {
                    throw IllegalStateException("Unsupported element type: " + fieldListElement.javaClass.name)
                }
            }
            fields = newFields
            newFields
        }
    }

    /**
     * Returns the resolved flat list of [FieldDetails], pairing each field with the
     * [FieldListIncludeElement] that contributed it (or `null` for directly declared fields).
     * The result is cached after the first call.
     */
    fun getFieldDetails(): ArrayList<FieldDetails> {
        return fieldDetails ?: run {
            val newFields = ArrayList<FieldDetails>()
            for (fieldListElement in fieldListElements) {
                if (fieldListElement is Field) {
                    newFields.add(FieldDetails(fieldListElement))
                } else if (fieldListElement is FieldListIncludeElement) {
                    val includedObject = dataObjectsByName[fieldListElement.include]
                        ?: throw IllegalStateException("Cannot find '" + fieldListElement.include)
                    val baseFields = if (fieldListElement.includeOnly.isNotEmpty()) {
                        getIncludedOnlyFields(
                            includedObject.computedFields,
                            fieldListElement.includeOnlyPathSet(),
                            Array.empty()
                        )
                    } else {
                        includedObject.computedFields
                    }
                    val includedFields = if (fieldListElement.excluding.isNotEmpty()) {
                        getIncludedFields(baseFields, fieldListElement.excludingPathSet(), Array.empty())
                    } else {
                        baseFields
                    }
                    newFields.addAll(includedFields.map { FieldDetails(it, fieldListElement) })
                } else {
                    throw IllegalStateException("Unsupported element type: " + fieldListElement.javaClass.name)
                }
            }
            fieldDetails = newFields
            newFields
        }
    }

    private fun getIncludedOnlyFields(
        fields: ArrayList<Field>,
        includeOnlyPathSet: FieldPathSet,
        parentPath: Array<String>
    ): ArrayList<Field> {
        val includedFields = ArrayList<Field>()

        val fieldNames = fields.stream().map { it.longName }.collect(HashSet.collector())
        val includeOnlyFieldNames = HashSet.ofAll(includeOnlyPathSet.map { it.field })
        if (!fieldNames.containsAll(includeOnlyFieldNames)) {
            val missedIncludes = includeOnlyFieldNames.removeAll(fieldNames)
            throw IllegalStateException(
                "'includeOnly' element refers to unknown field${if (missedIncludes.size() > 1) "s" else ""}: ${
                    missedIncludes.map { "'" + parentPath.joinToString(separator = ".", postfix = ".") + it + "'" }
                        .joinToString(separator = ", ")
                }"
            )
        }

        fields.forEach { field ->
            when (val node = includeOnlyPathSet[field.longName]) {
                is FieldPathLeaf -> includedFields.add(field)
                is FieldPathStem -> {
                    val newPath = parentPath.append(field.longName)
                    val subField = if (field.typeRef != null) {
                        val dataObject = dataObjectsByName[field.typeRef]
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.OBJECT,
                            fields = ArrayList(getIncludedOnlyFields(dataObject!!.computedFields, node.childPathElements, newPath))
                        )
                    } else if (field.type == DataType.ARRAY && field.items!!.typeRef != null) {
                        val dataObject = dataObjectsByName[field.items.typeRef]
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.ARRAY,
                            items = TypeSpec(
                                type = DataType.OBJECT,
                                fields = ArrayList(getIncludedOnlyFields(dataObject!!.computedFields, node.childPathElements, newPath))
                            )
                        )
                    } else if (field.type == DataType.OBJECT) {
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.OBJECT,
                            fields = ArrayList(getIncludedOnlyFields(field.computedFields, node.childPathElements, newPath)),
                        )
                    } else if (field.type == DataType.ARRAY && field.items!!.type == DataType.OBJECT) {
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = DataType.ARRAY,
                            items = TypeSpec(
                                type = DataType.OBJECT,
                                fields = ArrayList(
                                    getIncludedOnlyFields(
                                        field.items.computedFields,
                                        node.childPathElements,
                                        newPath
                                    )
                                )
                            )
                        )
                    } else {
                        throw IllegalStateException(
                            "Cannot include sub-fields of non-object type (type-ref or object): '${
                                newPath.joinToString(separator = ".")
                            }'"
                        )
                    }
                    includedFields.add(subField)
                }
                null -> {}
            }
        }

        return includedFields
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

    /** `true` if the underlying [fieldListElements] list is non-empty. */
    val hasFields: Boolean
        get() = fieldListElements.isNotEmpty()

    /**
     * Associates a [NamedType] as the logical owner of this field list, used for contextual
     * error messages during code generation.
     */
    fun setParentType(parentType: NamedType?) {
        this.parentType = parentType
    }
}
