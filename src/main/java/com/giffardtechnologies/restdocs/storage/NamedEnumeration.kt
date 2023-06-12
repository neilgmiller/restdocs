package com.giffardtechnologies.restdocs.storage

import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
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
) : TypeSpec(type = DataType.ENUM, null, key = key, values = values), NamedType, Validatable {

    override val typeName: String
        get() = name

    override fun validate(validationContext: Any?) {
        super.validate(validationContext)
        if (name.isBlank()) {
            throw ValidationException("Enumeration must have a name")
        }
        if (validationContext is DocValidator.AccumulatingContext) {
            if (validationContext.referencableTypes.contains(name)) {
                throw ValidationException("A data object or enumeration already exists with the name: \"$name\"")
            } else {
                validationContext.referencableTypes.add(name)
            }
        }
    }

}