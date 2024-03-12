package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.validateHasNoDuplicates

class DataObject(
    val name: String,
    val isHidden: Boolean = false,
    val description: String? = null,
    val fields: ArrayList<FieldListElement>,
    val discriminator: Field? = null,
    @JsonProperty("child types") val childTypes: ArrayList<DataObject>? = null,
    @JsonProperty("discriminator value") val discriminatorValue: String? = null,
) : Validatable {
    override fun validate(validationContext: Any?) {
        if (name.isBlank()) {
            throw ValidationException("Data object must have a name")
        }
        if (validationContext is DocValidator.AccumulatingContext) {
            if (validationContext.referencableTypes.contains(name)) {
                throw ValidationException("An data object or enumeration already exists with the name: \"$name\"")
            } else {
                fields.validateHasNoDuplicates()
                validationContext.referencableTypes.add(name)
            }
        } else {
            fields.validateHasNoDuplicates(validationContext.documentIfAvailable)
        }
    }

}