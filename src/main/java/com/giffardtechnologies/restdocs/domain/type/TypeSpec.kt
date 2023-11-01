package com.giffardtechnologies.restdocs.domain.type

import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.FieldElementList.setParentDocument
import com.giffardtechnologies.restdocs.domain.FieldListElement
import com.giffardtechnologies.restdocs.domain.Restriction
import com.google.gson.annotations.SerializedName

open class TypeSpec {
    @JvmField
    var type: DataType? = null
    @JvmField
    var interpretedAs: BasicType? = null

    @JvmField
    @SerializedName("typeref")
    var typeRef: String? = null
    @JvmField
    var key: KeyType? = null
    @JvmField
    var flagType: FlagType? = null
    @JvmField
    var items: TypeSpec? = null
    @JvmField
    var restrictions: ArrayList<Restriction>? = null

    /**
     * Used for translating objects, general case should use the list getters.
     *
     * @return the FieldElementList object for this object
     *
     * @see .getFieldListElements
     * @see .getFields
     */
    @JvmField
    @SerializedName("fields")
    val fieldElementList = FieldElementList()
    @JvmField
    var values: ArrayList<EnumConstant>? = null
    private var mParentDocument: Document? = null
    val hasInterpretedAs: Boolean
        get() = interpretedAs != null

    // For velocity
    fun getIsTypeRef(): Boolean {
        return typeRef != null
    }

    fun isTypeRef(): Boolean {
        return typeRef != null
    }

    val hasRestrictions: Boolean
        get() = restrictions != null && !restrictions!!.isEmpty()
    val fields: ArrayList<Field>?
        get() = fieldElementList.getFields()

    fun setFields(fields: ArrayList<Field?>?) {
        fieldElementList.setFields(fields)
    }

    fun hasFields(): Boolean {
        return fieldElementList.hasFields()
    }

    val hasFields: Boolean
        get() = fieldElementList.hasFields
    val fieldListElements: ArrayList<FieldListElement>?
        get() = fieldElementList.getFieldListElements()

    fun setFieldListElements(fieldListElements: ArrayList<FieldListElement?>?) {
        fieldElementList.setFieldListElements(fieldListElements)
    }

    var parentDocument: Document?
        get() = mParentDocument
        set(parentDocument) {
            mParentDocument = parentDocument
            fieldElementList.setParentDocument(mParentDocument)
            if (items != null) {
                items.setParentDocument(parentDocument)
            }
        }

    fun hasEnumValues(): Boolean {
        return values != null && !values!!.isEmpty()
    }

    val hasEnumValues: Boolean
        get() = hasEnumValues()
}
