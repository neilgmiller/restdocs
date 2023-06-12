package com.giffardtechnologies.restdocs.jackson.validation

interface Validatable {
    fun validate(validationContext: Any?)
}

class ValidationException(message: String) : Exception(message)