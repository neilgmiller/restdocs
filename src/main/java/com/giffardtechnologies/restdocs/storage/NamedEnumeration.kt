package com.giffardtechnologies.restdocs.storage

import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.KeyType
import com.giffardtechnologies.restdocs.storage.type.NamedType
import com.giffardtechnologies.restdocs.storage.type.TypeSpec

class NamedEnumeration(
    val name: String,
    val description: String? = null,
    key: KeyType,
    values: ArrayList<EnumConstant>,
) : TypeSpec(type = DataType.ENUM, null, key = key, values = values), NamedType {

    override val typeName: String
        get() = name

}