package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.validateHasNoDuplicates

/** The HTTP verb for an API method. */
enum class HTTPMethod {
    GET, PUT, POST, DELETE, HEAD, OPTIONS, TRACE, CONNECT
}

/**
 * Represents a single API endpoint (method) in the REST documentation.
 *
 * A method must have at least one of [id] or [path] to be valid.
 *
 * @property method The HTTP verb used to invoke this endpoint.
 * @property path The URL path of the endpoint, relative to the service base path.
 * @property protocolsAllowed The list of allowed protocols (e.g., `"http"`, `"https"`).
 * @property id An optional numeric identifier for the method.
 * @property name A unique programmatic name for this method, used for code generation.
 * @property description Human-readable description of what this endpoint does.
 * @property isAuthenticationRequired Whether callers must supply authentication credentials.
 * @property headers HTTP headers accepted by this method.
 * @property parameters Query or path parameters accepted by this method.
 * @property requestBody Description of the request body, if any.
 * @property response Description of the successful response body.
 * @property successCodes HTTP status codes that indicate success.
 * @property failureCodes HTTP status codes that indicate failure.
 */
data class Method(
    val method: HTTPMethod? = null,
    val path: String? = null,
    @field:JsonProperty("protocols allowed")
    val protocolsAllowed: ArrayList<String> = ArrayList(),
    val id: Int? = null,
    val name: String = "",
    val description: String? = "",
    @field:JsonProperty("authentication required")
    val isAuthenticationRequired: Boolean = true,
    val headers: ArrayList<Field>? = ArrayList(),
    val parameters: ArrayList<FieldListElement>? = null,
    @field:JsonProperty("request body")
    val requestBody: RequestBody? = null,
    val response: Response? = null,
    val asyncResponse: Response? = null,
    @field:JsonProperty("successful codes")
    val successCodes: ArrayList<String> = ArrayList(),
    @field:JsonProperty("failure codes")
    val failureCodes: ArrayList<String> = ArrayList(),
) : Validatable {
    /**
     * Validates this method, ensuring it has at least one of [id] or [path], and that parameter
     * names are unique. During accumulation phase, also checks that [name] is globally unique
     * among all methods.
     */
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

    override fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
        validate(validationContext)
        if (validationContext !is DocValidator.AccumulatingContext) {
            val ctx = validationContext as DocValidator.ValidationContext
            val responseIsAsync = response?.let { ctx.responseIsAsync(it) } ?: false
            // VALID-04: asyncResponse present but response has no job field
            if (asyncResponse != null && !responseIsAsync) {
                throw ValidationException("Method '$name': asyncResponse is present but response has no 'job' field")
            }
            // VALID-03: response has job field but no asyncResponse
            if (responseIsAsync && asyncResponse == null) {
                warningEmitter("WARNING: Method '$name': response has a job field but no asyncResponse block")
            }
        }
    }
}