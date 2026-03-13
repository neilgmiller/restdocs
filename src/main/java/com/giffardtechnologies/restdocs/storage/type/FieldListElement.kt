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

/**
 * Validates that this list of [FieldListElement] items contains no duplicate field [Field.name]
 * or [Field.longName] values.
 *
 * When [parentDocument] is provided, include elements are expanded before checking, so duplicates
 * introduced via [FieldListIncludeElement] are also detected.
 *
 * @param parentDocument The document used to resolve [FieldListIncludeElement] references.
 * @throws com.giffardtechnologies.restdocs.jackson.validation.ValidationException if duplicates are found.
 */
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

/**
 * Describes a single duplicate field name or long name found during validation.
 *
 * @property name The duplicated field name or long name.
 * @property duplicateCount The total number of occurrences of [name] in the field list.
 * @property duplicatesIncludedFrom The names of the [FieldListIncludeElement] sources that
 * contributed one or more of the duplicates, if any.
 */
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
 * A [FieldListElement] that inlines the fields of a referenced [com.giffardtechnologies.restdocs.storage.DataObject]
 * into the containing field list, with optional exclusions and required-state overrides.
 *
 * @property include The name of the data object whose fields are to be included.
 * @property excluding Long-name paths of fields (and sub-fields) to omit from the included set.
 * @property overrideRequired When set, overrides the [Field.isRequired] value for included fields,
 * except those listed in [RequiredOverride.excluding].
 */
class FieldListIncludeElement(
    /**
     * A reference to a DataObject, all fields of that object will be included.
     */
    val include: String,
    val includeOnly: ArrayList<String> = ArrayList(),
    val excluding: ArrayList<String> = ArrayList(),
    val overrideRequired: RequiredOverride? = null,
) : FieldListElement, Validatable {

    /**
     * Returns the [excluding] paths as a [FieldPathSet] for efficient lookup during field
     * resolution.
     */
    fun excludingPathSet(): FieldPathSet {
        val fieldPaths = excluding.map { FieldPath(it) }
        return FieldPathSet.ofAll(fieldPaths)
    }

    /**
     * Returns the [includeOnly] paths as a [FieldPathSet] for efficient lookup during field
     * resolution.
     */
    fun includeOnlyPathSet(): FieldPathSet {
        val fieldPaths = includeOnly.map { FieldPath(it) }
        return FieldPathSet.ofAll(fieldPaths)
    }

    /**
     * Validates this include element against a full context, checking that [include] refers to a
     * known type and that all [includeOnly] and [excluding] paths and [overrideRequired] exclusions
     * refer to existing fields. When both [includeOnly] and [excluding] are set, also validates
     * that each [excluding] path's first-segment field is covered by [includeOnly].
     */
    override fun validate(validationContext: Any?) {
        if (validationContext is DocValidator.FullContext) {
            if (!validationContext.referencableTypes.contains(include)) {
                throw ValidationException("'include' element reference refers to missing type: '$include'")
            }
            val dataObject = validationContext.document.dataObjects.first { it.name == include }
            val fields = dataObject.fields

            if (includeOnly.isNotEmpty()) {
                val includeOnlyPathSet = includeOnlyPathSet()
                validateExclusions(fields, includeOnlyPathSet, validationContext, elementName = "includeOnly")

                if (excluding.isNotEmpty()) {
                    val excludingPathSet = excludingPathSet()
                    excludingPathSet.forEach { excludePath ->
                        if (includeOnlyPathSet[excludePath.field] == null) {
                            throw ValidationException(
                                "'excluding' path '${excludePath.field}' refers to a field not included by 'includeOnly'"
                            )
                        }
                    }
                    validateExclusions(fields, excludingPathSet, validationContext, elementName = "excluding")
                }
            } else {
                validateExclusions(fields, excludingPathSet(), validationContext, elementName = "excluding")
            }

            overrideRequired?.validate(validationContext, fields)
        }
    }
}

/**
 * Overrides the [Field.isRequired] value for fields brought in by a [FieldListIncludeElement].
 *
 * @property required The required state to apply to included fields.
 * @property excluding Long-name paths of included fields that should keep their original required
 * state and not be overridden.
 */
class RequiredOverride(
    val required: Boolean,
    val excluding: ArrayList<String>? = null
) {
    /**
     * Validates that all paths in [excluding] refer to actual fields in the included [fields] list.
     *
     * @param context The full validation context used to resolve type references.
     * @param fields The field list of the data object being included.
     */
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