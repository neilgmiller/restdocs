package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.vavr.isNotEmpty
import io.vavr.collection.Array

class Method(
    val method: HTTPMethod,
    val path: String? = null,
    val protocolsAllowed: Array<String> = Array.of("HTTP"),
    val id: Int? = null,
    val name: String,
    val isAuthenticationRequired: Boolean,
    val parameters: FieldElementList,
    var failureCodes: Array<String> = Array.empty(),
    var successCodes: Array<String> = Array.empty(),
    var response: Response? = null,
    var requestBody: RequestBody? = null,
    val headers: Array<Field> = Array.empty(),
    val description: String? = null
) {
    enum class HTTPMethod {
        GET,
        PUT,
        POST,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE,
        CONNECT
    }


    val hasID: Boolean
        get() = id != null
    val hasDescription: Boolean
        get() = !description.isNullOrEmpty()

    fun getIsAuthenticationRequired(): Boolean {
        return isAuthenticationRequired
    }

    val hasHeaders: Boolean
        get() = headers.isNotEmpty()
    val hasParameters: Boolean
        get() = parameters.fields.isNotEmpty()

    fun getParameters(): Array<Field> {
        return parameters.fields
    }

    val hasRequestBody: Boolean
        get() = requestBody != null
}
