package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.model.FieldPath
import com.giffardtechnologies.restdocs.model.FieldPathLeaf
import com.giffardtechnologies.restdocs.model.FieldPathSet
import com.giffardtechnologies.restdocs.model.FieldPathStem
import com.giffardtechnologies.restdocs.vavr.mapNonNull
import io.vavr.collection.Array
import io.vavr.collection.HashSet

/**
 * A class that manages a list of fields whether a straight list or an include-reference
 */
class FieldElementList(
    private val fieldListElements: Array<out FieldListElement>,
    private val parentType: DataObject? = null
) {

    private var _fields: Array<Field>? = null

    val fields: Array<Field>
        get() {
        return _fields ?: run {
            val newFields = fieldListElements
                .flatMap { fieldListElement: FieldListElement ->
                    return@flatMap when (fieldListElement) {
                        is Field -> {
                            Array.of(fieldListElement)
                        }

                        is FieldListIncludeElement -> {
                            val includedObject = fieldListElement.include
                            val baseFields = if (fieldListElement.includeOnly.isEmpty) {
                                includedObject.type.fields
                            } else {
                                val includeOnlyPathSet = FieldPathSet.ofAll(fieldListElement.includeOnly.map { FieldPath(it) })
                                getIncludedOnlyFields(includedObject.type.fields, includeOnlyPathSet, Array.empty())
                            }
                            val includedFields = if (fieldListElement.excluding.isEmpty) {
                                baseFields
                            } else {
                                val excludingPathSet = FieldPathSet.ofAll(fieldListElement.excluding.map { FieldPath(it) })
                                getIncludedFields(baseFields, excludingPathSet, Array.empty())
                            }
                            val overrideRequired = fieldListElement.overrideRequired
                            if (overrideRequired == null) {
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
                        }
                    }
                }

            _fields = newFields
            newFields
        }
    }

    private fun getIncludedFields(
        fields: Array<Field>,
        excludingPathSet: FieldPathSet,
        parentPath: Array<String>
    ): Array<Field> {
        if (excludingPathSet.isEmpty()) {
            return fields
        } else {
            // validate first level fields
            val fieldNames = fields.map { it.longName }.collect(HashSet.collector())
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

            return fields.mapNonNull { field ->
                when (val node = excludingPathSet[field.longName]) {
                    is FieldPathLeaf -> null
                    is FieldPathStem -> {
                        val newPath = parentPath.append(field.longName)
                        Field(
                            name = field.name,
                            longName = field.longName,
                            type = getIncludedFieldTypeSpec(field.type, node.childPathElements, newPath),
                            description = field.description,
                            defaultValue = field.defaultValue,
                            isRequired = field.isRequired,
                            sampleValues = field.sampleValues,
                        )
                    }

                    null -> field
                }
            }
        }
   }

    private fun getIncludedFieldTypeSpec(
        typeSpec: TypeSpec,
        childPathElements: FieldPathSet,
        newPath: Array<String>
    ): TypeSpec {
        return when (typeSpec) {
            is TypeSpec.TypeRefSpec -> {
                getIncludedFieldTypeSpec(typeSpec.typeRef.value.type, childPathElements, newPath)
            }
            is TypeSpec.ObjectSpec -> {
                TypeSpec.ObjectSpec(
                    fieldElementList = FieldElementList(
                        fieldListElements = getIncludedFields(typeSpec.fields, childPathElements, newPath)
                    )
                )
            }
            is TypeSpec.ArraySpec -> {
                TypeSpec.ArraySpec(
                    getIncludedFieldTypeSpec(typeSpec.items, childPathElements, newPath)
                )
            }
            is TypeSpec.BitSetSpec<*>,
            is TypeSpec.DataSpec,
            is TypeSpec.BooleanSpec,
            is TypeSpec.MapSpec<*>,
            is TypeSpec.EnumSpec<*> -> {
                throw IllegalStateException(
                    "Cannot exclude sub-fields of non-object type (type-ref or object): '${
                        newPath.joinToString(
                            separator = "."
                        )
                    }'"
                )
            }
        }
    }

    private fun getIncludedOnlyFields(
        fields: Array<Field>,
        includeOnlyPathSet: FieldPathSet,
        parentPath: Array<String>
    ): Array<Field> {
        // validate first level fields
        val fieldNames = fields.map { it.longName }.collect(HashSet.collector())
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

        return fields.mapNonNull { field ->
            when (val node = includeOnlyPathSet[field.longName]) {
                is FieldPathLeaf -> field
                is FieldPathStem -> {
                    val newPath = parentPath.append(field.longName)
                    Field(
                        name = field.name,
                        longName = field.longName,
                        type = getIncludedOnlyFieldTypeSpec(field.type, node.childPathElements, newPath),
                        description = field.description,
                        defaultValue = field.defaultValue,
                        isRequired = field.isRequired,
                        sampleValues = field.sampleValues,
                    )
                }
                null -> null
            }
        }
    }

    private fun getIncludedOnlyFieldTypeSpec(
        typeSpec: TypeSpec,
        childPathElements: FieldPathSet,
        newPath: Array<String>
    ): TypeSpec {
        return when (typeSpec) {
            is TypeSpec.TypeRefSpec -> {
                getIncludedOnlyFieldTypeSpec(typeSpec.typeRef.value.type, childPathElements, newPath)
            }
            is TypeSpec.ObjectSpec -> {
                TypeSpec.ObjectSpec(
                    fieldElementList = FieldElementList(
                        fieldListElements = getIncludedOnlyFields(typeSpec.fields, childPathElements, newPath)
                    )
                )
            }
            is TypeSpec.ArraySpec -> {
                TypeSpec.ArraySpec(
                    getIncludedOnlyFieldTypeSpec(typeSpec.items, childPathElements, newPath)
                )
            }
            is TypeSpec.BitSetSpec<*>,
            is TypeSpec.DataSpec,
            is TypeSpec.BooleanSpec,
            is TypeSpec.MapSpec<*>,
            is TypeSpec.EnumSpec<*> -> {
                throw IllegalStateException(
                    "Cannot include sub-fields of non-object type (type-ref or object): '${
                        newPath.joinToString(separator = ".")
                    }'"
                )
            }
        }
    }

}
