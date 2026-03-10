package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.model.FieldPath
import com.giffardtechnologies.restdocs.model.FieldPathSet
import com.giffardtechnologies.restdocs.model.FieldPathStem
import com.giffardtechnologies.restdocs.storage.Document
import io.vavr.collection.Array

/**
 * A marker interface for types that can go in a field list
 *
 * @see FieldElementList
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
sealed interface FieldListElement

fun List<FieldListElement>.validateHasNoDuplicates(parentDocument: Document? = null) {
    val fields = if (parentDocument == null) {
        this.filterIsInstance<Field>().map { FieldDetails(it) }
    } else {
        FieldElementList(parentDocument, this).getFieldDetails()
    }
    val duplicates = fields.findDuplicates { it.field.name }
    if (duplicates.isNotEmpty()) {
        throw ValidationException("Object has duplicate field names: $duplicates")
    }
    // skip blanks in the duplicate check, as they were allowed by [ValidationOptions.longNameIsOptional]
    val duplicateLongNames = fields.filter { it.field.longName.isNotBlank() }.findDuplicates { it.field.longName }
    if (duplicateLongNames.isNotEmpty()) {
        throw ValidationException("Object has duplicate field long names: $duplicateLongNames")
    }
}

private fun List<FieldDetails>.findDuplicates(keySelector: (FieldDetails) -> String): List<DuplicateDetails> {
    val grouping = this.groupBy(keySelector) { it }
    val duplicates = grouping.filter { it.value.size > 1 }
    return duplicates.entries.map { entry ->
        val includes = entry.value.mapNotNull { it.includedBy?.include }
        DuplicateDetails(entry.key, entry.value.size, includes)
    }
}

data class DuplicateDetails(val name: String, val duplicateCount: Int, val duplicatesIncludedFrom: List<String>) {
    override fun toString(): String {
        return if (duplicatesIncludedFrom.isEmpty()) {
            "'$name' is duplicated $duplicateCount times"
        } else {
            "'$name' is duplicated $duplicateCount times, including from data objects: $duplicatesIncludedFrom)"
        }
    }
}

/**
 * A class for including the fields of data object inline with a list of fields
 */
class FieldListIncludeElement(
    /**
     * A reference to a DataObject, all fields of that object will be included
     */
    val include: String,
    val excluding: ArrayList<String> = ArrayList(),
    val overrideRequired: RequiredOverride? = null,
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

            validateExclusions(fields, excludingPathSet, validationContext, elementName = "excluding")

            overrideRequired?.validate(validationContext, fields)
        }
    }
}

class RequiredOverride(
    val required: Boolean,
    val excluding: ArrayList<String>? = null
) {
    fun validate(context: DocValidator.FullContext, fields: ArrayList<FieldListElement>) {
        if (!excluding.isNullOrEmpty()) {
            val excludingPathSet = FieldPathSet.ofAll(excluding.map { FieldPath(it) })
            validateExclusions(
                fields,
                excludingPathSet,
                context,
                elementName = "override.excluding",
            )
        }
    }
}

private fun validateExclusions(
    fields: ArrayList<FieldListElement>,
    excludingPathSet: FieldPathSet,
    validationContext: DocValidator.FullContext,
    elementName: String,
    parentPath: Array<String> = Array.empty(),
) {
    val fieldInstances = fields.filterIsInstance<Field>()

    // validate first level fields
    val fieldNames = fieldInstances.map { it.longName }.toSet()
    val excludedFieldNames = excludingPathSet.map { it.field }.toSet()
    if (!fieldNames.containsAll(excludedFieldNames)) {
        val missedExcludes = excludedFieldNames - fieldNames
        throw ValidationException(
            "'$elementName' element refers to unknown field${if (missedExcludes.size > 1) "s" else ""}: ${
                missedExcludes.joinToString(
                    separator = ", "
                ) { "'" + parentPath.joinToString(separator = ".", postfix = ".") + it + "'" }
            }"
        )
    }

    // validate sub-objects
    val excludedFieldInSubObjects = excludingPathSet.filterIsInstance<FieldPathStem>().associateBy { it.field }
    fieldInstances.mapNotNull { field -> excludedFieldInSubObjects[field.longName]?.let { Pair(field, it) } }
        .forEach { pair ->
            val (field, fieldPathStem) = pair
            val newPath = parentPath.append(field.longName)
            if (field.typeRef != null) {
                val dataObject = validationContext.document.dataObjects.first { it.name == field.typeRef }
                validateExclusions(
                    dataObject.fields,
                    fieldPathStem.childPathElements,
                    validationContext,
                    elementName,
                    newPath
                )
            } else if (field.type == DataType.ARRAY && field.items!!.typeRef != null) {
                val dataObject = validationContext.document.dataObjects.first { it.name == field.items.typeRef }
                validateExclusions(
                    dataObject.fields,
                    fieldPathStem.childPathElements,
                    validationContext,
                    elementName,
                    newPath
                )
            } else if (field.type == DataType.OBJECT) {
                validateExclusions(
                    field.fields!!,
                    fieldPathStem.childPathElements,
                    validationContext,
                    elementName,
                    newPath
                )
            } else if (field.type == DataType.ARRAY && field.items!!.type == DataType.OBJECT) {
                validateExclusions(
                    field.items.fields!!,
                    fieldPathStem.childPathElements,
                    validationContext,
                    elementName,
                    newPath
                )
            } else {
                throw ValidationException("Cannot exclude sub-fields of non-object type (type-ref or object): '${newPath.joinToString(separator = ".")}'")
            }
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