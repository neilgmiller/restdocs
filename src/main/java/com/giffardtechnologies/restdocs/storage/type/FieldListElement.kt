package com.giffardtechnologies.restdocs.storage.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.Restriction

/**
 * A marker interface for types that can go in a field list
 *
 * @see FieldElementList
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.DEDUCTION)
sealed interface FieldListElement

/**
 * A class for including the fields of data object inline with the a list of fields
 */
class FieldListIncludeElement(
    /**
     * A reference to a DataObject, all fields of that object will be included
     */
    val include: String,
    val excluding: ArrayList<String> = ArrayList()
) : FieldListElement, Validatable {
    override fun validate(validationContext: Any?) {
        if (validationContext is DocValidator.FullContext) {
            if (!validationContext.referencableTypes.contains(include)) {
                throw ValidationException("'include' element reference refers to missing type: '$include'")
            }
            val dataObject = validationContext.document.dataObjects.first { it.name == include }
            val fieldNames = dataObject.fields.filterIsInstance(Field::class.java).map { it.longName }.toSet()
            if (!fieldNames.containsAll(excluding)) {
                val missedExcludes = HashSet(excluding)
                missedExcludes.removeAll(fieldNames)
                throw ValidationException("'excluding' element refers to unknown column(s): '$missedExcludes'")
            }
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