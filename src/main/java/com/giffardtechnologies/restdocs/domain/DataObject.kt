package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.google.gson.annotations.SerializedName

class DataObject(
    override val typeName: String,
    override val type: TypeSpec.ObjectSpec,
    val description: String? = null,
    val isHidden: Boolean = false,
) : NamedType<TypeSpec.ObjectSpec> {

    // future feature support
    var discriminator: Field? = null
    var childTypes: ArrayList<DataObject>? = null
    var discriminatorValue: String? = null

    fun hasChildren(): Boolean {
        return childTypes != null && !childTypes!!.isEmpty() && discriminator != null
    }

    val hasChildren: Boolean
        get() = childTypes != null && !childTypes!!.isEmpty() && discriminator != null

    override fun toString(): String {
        return typeName
    }
}
