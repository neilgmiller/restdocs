package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.NamedType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec

class NamedBitSet(
    override val typeName: String,
    val description: String?,
    override val type: TypeSpec.BitSetSpec<*>
) : NamedType<TypeSpec.BitSetSpec<*>> {

    override fun toString(): String {
        return "BitSet: $typeName"
    }
}
