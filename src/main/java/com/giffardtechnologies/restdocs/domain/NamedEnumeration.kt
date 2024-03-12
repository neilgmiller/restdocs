package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec

class NamedEnumeration(
    override val typeName: String,
    val description: String?,
    override val type: TypeSpec.EnumSpec<*>
) : NamedType<TypeSpec.EnumSpec<*>> {

    override fun toString(): String {
        return "Enumeration: $typeName"
    }
}
