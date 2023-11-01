package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec

class NamedEnumeration : TypeSpec(), NamedType {
    @JvmField
    var name: String? = null
    var description: String? = null

    init {
        type = DataType.ENUM
    }

    override fun toString(): String {
        return "Enumeration: $name"
    }

    override val typeName: String?
        get() = name
}
