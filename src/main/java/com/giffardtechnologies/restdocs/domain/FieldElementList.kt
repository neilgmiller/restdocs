package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.Field
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
                .flatMap { fieldListElement ->
                    return@flatMap if (fieldListElement is Field) {
                        Array.of(fieldListElement)
                    } else if (fieldListElement is FieldListIncludeElement) {
                        val includedObject = fieldListElement.include
                        if (fieldListElement.excluding.isEmpty) {
                            includedObject.type.fields
                        } else {
                            val fieldPaths = fieldListElement.excluding.map { FieldPath(it) }
                            val excludingPathSet = FieldPathSet.ofAll(fieldPaths)

                            val fields = includedObject.type.fields

                            val fieldsToAdd = getIncludedFields(fields, excludingPathSet, Array.empty())

                            fieldsToAdd
                        }
                    } else {
                        throw IllegalStateException("Unsupported element type: " + fieldListElement.javaClass.name)
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
            when(val node = excludingPathSet[field.longName]) {
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

    private fun getIncludedFieldTypeSpec(
        typeSpec: TypeSpec,
        childPathElements: FieldPathSet,
        newPath: Array<String>
    ): TypeSpec {
        return when (typeSpec) {
            is TypeSpec.TypeRefSpec -> {
                getIncludedFieldTypeSpec(typeSpec.typeRef.type, childPathElements, newPath)
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

}
