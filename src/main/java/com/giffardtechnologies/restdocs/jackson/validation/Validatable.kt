package com.giffardtechnologies.restdocs.jackson.validation

interface Validatable {
    fun validate(validationContext: Any?)

    fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
        validate(validationContext)
    }
}

class ValidationException(message: String) : Exception(message)