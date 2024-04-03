package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.validateHasNoDuplicates

enum class HTTPMethod {
    GET, PUT, POST, DELETE, HEAD, OPTIONS, TRACE, CONNECT
}

data class Method(
    val method: HTTPMethod? = null,
    val path: String? = null,
    @JsonProperty("protocols allowed")
    val protocolsAllowed: ArrayList<String> = ArrayList(),
    val id: Int? = null,
    val name: String = "",
    val description: String? = "",
    @JsonProperty("authentication required")
    val isAuthenticationRequired: Boolean = true,
    val headers: ArrayList<Field>? = ArrayList(),
    val parameters: ArrayList<FieldListElement>? = null,
    @JsonProperty("request body")
    val requestBody: RequestBody? = null,
    val response: Response? = null,
    @JsonProperty("successful codes")
    val successCodes: ArrayList<String> = ArrayList(),
    @JsonProperty("failure codes")
    val failureCodes: ArrayList<String> = ArrayList(),
) : Validatable {
    override fun validate(validationContext: Any?) {
        if (path == null && id == null) {
            throw ValidationException("A method must have at least one of 'id' and 'path'")
        }
        if (validationContext is DocValidator.AccumulatingContext) {
            if (validationContext.methodClassNames.contains(name)) {
                throw ValidationException("A method already exists with the name: \"$name\"")
            } else {
                parameters?.validateHasNoDuplicates()
                validationContext.methodClassNames.add(name)
            }
        } else {
            parameters?.validateHasNoDuplicates(validationContext.documentIfAvailable)
        }

    }
}