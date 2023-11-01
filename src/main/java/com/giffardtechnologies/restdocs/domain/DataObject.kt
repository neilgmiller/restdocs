package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.google.gson.annotations.SerializedName

class DataObject : NamedType {
    @JvmField
    var name: String? = null
    var isHidden = false
    var description: String? = null

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
    var discriminator: Field? = null

    @SerializedName("child types")
    var childTypes: ArrayList<DataObject>? = null

    @SerializedName("discriminator value")
    var discriminatorValue: String? = null

    @Transient
    private var parent: Document? = null
    override val type: DataType?
        get() = DataType.OBJECT
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

    fun hasChildren(): Boolean {
        return childTypes != null && !childTypes!!.isEmpty() && discriminator != null
    }

    val hasChildren: Boolean
        get() = childTypes != null && !childTypes!!.isEmpty() && discriminator != null

    override fun toString(): String {
        return name!!
    }

    override val typeName: String?
        get() = name

    fun setParent(parent: Document?) {
        this.parent = parent
        fieldElementList.setParentDocument(parent)
        fieldElementList.setParentType(this)
    }
}
