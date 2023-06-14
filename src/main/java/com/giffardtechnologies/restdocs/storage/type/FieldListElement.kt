package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.model.FieldPath
import com.giffardtechnologies.restdocs.model.FieldPathSet
import com.giffardtechnologies.restdocs.model.FieldPathStem
import io.vavr.collection.Array
import io.vavr.collection.HashMap
import io.vavr.collection.HashSet
import java.util.stream.Stream

/**
 * A marker interface for types that can go in a field list
 *
 * @see FieldElementList
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
sealed interface FieldListElement

/**
 * A class for including the fields of data object inline with a list of fields
 */
class FieldListIncludeElement(
    /**
     * A reference to a DataObject, all fields of that object will be included
     */
    val include: String,
    val excluding: ArrayList<String> = ArrayList()
) : FieldListElement, Validatable {

    fun excludingPathSet(): FieldPathSet {
        val fieldPaths = excluding.map { FieldPath(it) }
        return FieldPathSet.ofAll(fieldPaths)
    }

    override fun validate(validationContext: Any?) {
        if (validationContext is DocValidator.FullContext) {
            if (!validationContext.referencableTypes.contains(include)) {
                throw ValidationException("'include' element reference refers to missing type: '$include'")
            }
            val dataObject = validationContext.document.dataObjects.first { it.name == include }

            val fields = dataObject.fields
            val excludingPathSet = excludingPathSet()

            validateExclusions(fields, excludingPathSet, validationContext, Array.empty())
        }
    }

    private fun validateExclusions(
        fields: ArrayList<FieldListElement>,
        excludingPathSet: FieldPathSet,
        validationContext: DocValidator.FullContext,
        parentPath: Array<String>
    ) {
        val fieldInstances = fields.filterIsInstance(Field::class.java)

        // validate first level fields
        val fieldNames = fieldInstances.stream().map { it.longName }.collect(HashSet.collector())
        val excludedFieldNames = HashSet.ofAll(excludingPathSet.map { it.field })
        if (!fieldNames.containsAll(excludedFieldNames)) {
            val missedExcludes = excludedFieldNames.removeAll(fieldNames)
            throw ValidationException(
                "'excluding' element refers to unknown field${if (missedExcludes.size() > 1) "s" else ""}: ${
                    missedExcludes.map { "'" + parentPath.joinToString(separator = ".", postfix = ".") + it + "'" }
                        .joinToString(
                            separator = ", "
                        )
                }"
            )
        }

        // validate sub-objects
        val excludedFieldInSubObjects = excludingPathSet.filterIsInstance(FieldPathStem::class.java).stream()
            .collect(HashMap.collector { it.field })
        fieldInstances.mapNotNull { field -> excludedFieldInSubObjects[field.longName].orNull?.let { Pair(field, it) } }
            .forEach { pair ->
                val (field, fieldPathStem) = pair
                val newPath = parentPath.append(field.longName)
                if (field.typeRef != null) {
                    val dataObject = validationContext.document.dataObjects.first { it.name == field.typeRef }
                    validateExclusions(dataObject.fields, fieldPathStem.childPathElements, validationContext, newPath)
                } else if (field.type == DataType.ARRAY && field.items!!.typeRef != null) {
                    val dataObject = validationContext.document.dataObjects.first { it.name == field.items.typeRef }
                    validateExclusions(dataObject.fields, fieldPathStem.childPathElements, validationContext, newPath)
                } else if (field.type == DataType.OBJECT) {
                    validateExclusions(field.fields!!, fieldPathStem.childPathElements, validationContext, newPath)
                } else if (field.type == DataType.ARRAY && field.items!!.type == DataType.OBJECT) {
                    validateExclusions(field.items.fields!!, fieldPathStem.childPathElements, validationContext, newPath)
                } else {
                    throw ValidationException("Cannot exclude sub-fields of non-object type (type-ref or object): '${newPath.joinToString(separator = ".")}'")
                }
            }
    }
}

private fun <R> Stream<*>.filterIsInstance(klass: Class<R>): Stream<R> {
    return this.filter { klass.isInstance(it) }.map {
        @Suppress("UNCHECKED_CAST")
        it as R
    }
}

//class CombinedFieldListElement(
//    val name: String? = null,
//    val longName: String = "",
//    val description: String? = null,
//    @JsonProperty("default")
//    val defaultValue: String? = null,
//    @JsonProperty("required")
//    val isRequired: Boolean = true,
//    val sampleValues: List<String>? = null,
//    val type: DataType? = null,
//    @JsonProperty("typeref")
//    val typeRef: String? = null,
//    val key: KeyType? = null,
//    val flagType: FlagType? = null,
//    val items: TypeSpec? = null,
//    val restrictions: ArrayList<Restriction>? = null,
//    val fields: ArrayList<CombinedFieldListElement>? = null,
//    val values: ArrayList<EnumConstant>? = null,
//    val include: String? = null,
//    val excluding: ArrayList<String> = ArrayList(),
//)