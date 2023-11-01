package com.giffardtechnologies.restdocs.domain.type

import com.giffardtechnologies.restdocs.domain.FieldListElement
import com.google.gson.annotations.SerializedName

open class Field : TypeSpec(), FieldListElement {
    @JvmField
    var name: String? = null
    @JvmField
    var longName = ""
    var description: String? = null

    @JvmField
    @SerializedName("default")
    var defaultValue: String? = null
    var isRequired = true
    var parent: NamedType? = null
    private val sampleValues: List<String>? = null
    fun hasDefaultValue(): Boolean {
        return defaultValue != null
    }

    val hasDefaultValue: Boolean
        // for velocity templating
        get() = hasDefaultValue()

    override fun toString(): String {
        return "Field{" +
                "name='" + name + '\'' +
                ", longName='" + longName + '\'' +
                ", description='" + description + '\'' +
                ", required=" + isRequired +
                '}'
    }
}
