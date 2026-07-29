package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.documentIfAvailable
import com.giffardtechnologies.restdocs.jackson.validation.Validatable
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.BasicType
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.validateHasNoDuplicates
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/** The HTTP verb for an API method. */
enum class HTTPMethod {
    GET, PUT, POST, DELETE, HEAD, OPTIONS, TRACE, CONNECT
}

/**
 * How a method's async response shape behaves.
 *
 * [ALWAYS]: the method unconditionally returns its async shape (both [Method.jobResponse] and
 * [Method.payloadResponse] are always relevant) — no runtime switch.
 * [CONDITIONAL]: the method's sync/async behavior is chosen at runtime by a boolean parameter,
 * resolved via [Method.asyncControlParameter] or the service-level
 * [Common.asyncControlParameterName] default.
 */
enum class AsyncMode { ALWAYS, CONDITIONAL }

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
 * @property response Description of the successful response body for a plain synchronous method.
 * @property payloadResponse For an async method (pure, mixed, or conditional), the real data
 * payload — returned directly by sync-mode calls, or obtained by polling after [jobResponse] is
 * returned by async-mode calls.
 * @property jobResponse For an async method (pure, mixed, or conditional), the job envelope
 * returned immediately by async-mode calls, to be polled until [payloadResponse] is available.
 * @property successCodes HTTP status codes that indicate success.
 * @property failureCodes HTTP status codes that indicate failure.
 * @property asyncMode Required whenever [jobResponse] or [payloadResponse] is set (and forbidden
 * otherwise): whether this method's async shape is unconditional ([AsyncMode.ALWAYS]) or chosen
 * at runtime by a boolean parameter ([AsyncMode.CONDITIONAL]).
 * @property asyncControlParameter Overrides [Common.asyncControlParameterName] for this method:
 * the name of the boolean parameter that switches this method between returning its payload
 * synchronously and returning an async job to poll instead. Only valid when [asyncMode] is
 * [AsyncMode.CONDITIONAL].
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
    val payloadResponse: Response? = null,
    val jobResponse: Response? = null,
    @field:JsonProperty("successful codes")
    val successCodes: ArrayList<String> = ArrayList(),
    @field:JsonProperty("failure codes")
    val failureCodes: ArrayList<String> = ArrayList(),
    val deprecated: Boolean = false,
    val deprecationNote: String? = null,
    val deprecatedSince: String? = null,
    @field:JsonProperty("async mode")
    val asyncMode: AsyncMode? = null,
    @field:JsonProperty("async control parameter")
    val asyncControlParameter: String? = null,
) : Validatable {
    /**
     * Returns the effective name of the boolean parameter that controls this method's sync/async
     * response, resolving [asyncControlParameter] against the service-level
     * [Common.asyncControlParameterName] default, or `null` if neither is set.
     */
    fun resolveAsyncControlParameterName(commonDefault: String?): String? =
        asyncControlParameter ?: commonDefault

    /**
     * Returns the [Field] among [parameters] matching the resolved async-control parameter name,
     * or `null` if no such name is resolved or no parameter matches it.
     */
    fun resolveAsyncControlParameterField(commonDefault: String?): Field? {
        val name = resolveAsyncControlParameterName(commonDefault) ?: return null
        return parameters?.filterIsInstance<Field>()?.firstOrNull { it.name == name }
    }
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

        if (!deprecated) {
            if (deprecationNote != null) {
                throw ValidationException("Method '$name': 'deprecationNote' can only be set when 'deprecated' is true")
            }
            if (deprecatedSince != null) {
                throw ValidationException("Method '$name': 'deprecatedSince' can only be set when 'deprecated' is true")
            }
        } else {
            if (deprecationNote.isNullOrBlank()) {
                throw ValidationException("Method '$name': 'deprecationNote' is required when 'deprecated' is true")
            }
            if (deprecatedSince != null) {
                val parsedDeprecatedSince = try {
                    LocalDate.parse(deprecatedSince)
                } catch (e: IllegalArgumentException) {
                    throw ValidationException(
                        "Method '$name': 'deprecatedSince' must be an ISO-8601 date (yyyy-MM-dd), got: '$deprecatedSince'"
                    )
                }
                val today = java.time.LocalDate.now().toKotlinLocalDate()
                if (parsedDeprecatedSince > today) {
                    throw ValidationException(
                        "Method '$name': 'deprecatedSince' ($deprecatedSince) cannot be a future date"
                    )
                }
            }
        }
    }

    override fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
        validate(validationContext)
        if (validationContext !is DocValidator.AccumulatingContext) {
            val ctx = validationContext as DocValidator.ValidationContext
            val commonDefault = ctx.documentIfAvailable?.service?.common?.asyncControlParameterName
            val isAsyncMethod = jobResponse != null || payloadResponse != null

            if (response != null && isAsyncMethod) {
                throw ValidationException(
                    "Method '$name': 'response' cannot be set together with 'jobResponse' or 'payloadResponse'"
                )
            }

            if (isAsyncMethod && asyncMode == null) {
                throw ValidationException(
                    "Method '$name': 'async mode' is required when 'jobResponse' or 'payloadResponse' is set"
                )
            }
            if (!isAsyncMethod && asyncMode != null) {
                throw ValidationException(
                    "Method '$name': 'async mode' cannot be set on a method with neither 'jobResponse' nor 'payloadResponse'"
                )
            }

            if (asyncControlParameter != null && asyncMode != AsyncMode.CONDITIONAL) {
                throw ValidationException(
                    "Method '$name': 'async control parameter' can only be set when 'async mode' is 'conditional'"
                )
            }

            when (asyncMode) {
                AsyncMode.CONDITIONAL -> {
                    val asyncControlName = resolveAsyncControlParameterName(commonDefault)
                        ?: throw ValidationException(
                            "Method '$name': 'async mode' is 'conditional' but no async control parameter " +
                                "name is resolved (set 'async control parameter' on this method or the " +
                                "service-level default)"
                        )
                    val asyncControlField = resolveAsyncControlParameterField(commonDefault)
                        ?: throw ValidationException(
                            "Method '$name': async control parameter '$asyncControlName' is not present in 'parameters'"
                        )
                    val isBoolean = asyncControlField.type == DataType.BOOLEAN ||
                        asyncControlField.interpretedAs == BasicType.BOOLEAN
                    if (!isBoolean) {
                        throw ValidationException(
                            "Method '$name': async control parameter '${asyncControlField.name}' must be a boolean parameter (or interpreted as one)"
                        )
                    }
                    // conditional async: both fields are a hard requirement — the method cannot
                    // function correctly at runtime otherwise.
                    if (jobResponse == null || payloadResponse == null) {
                        throw ValidationException(
                            "Method '$name': has an async control parameter but is missing 'jobResponse' and/or 'payloadResponse'"
                        )
                    }
                }
                AsyncMode.ALWAYS -> {
                    if (jobResponse == null) {
                        throw ValidationException(
                            "Method '$name': 'async mode' is 'always' but has 'payloadResponse' with no 'jobResponse'"
                        )
                    }
                    if (payloadResponse == null) {
                        throw ValidationException(
                            "Method '$name': 'async mode' is 'always' but has 'jobResponse' with no 'payloadResponse'"
                        )
                    }
                }
                null -> Unit
            }

            // deprecated but no deprecatedSince recorded
            if (deprecated && deprecatedSince == null) {
                warningEmitter("WARNING: Method '$name': deprecated but has no 'deprecatedSince' date")
            }
        }
    }
}