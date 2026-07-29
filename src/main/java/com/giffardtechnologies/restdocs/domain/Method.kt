package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.vavr.isNotEmpty
import io.vavr.collection.Array
import kotlinx.datetime.LocalDate

class Method(
    val method: HTTPMethod,
    val path: String? = null,
    val protocolsAllowed: Array<String> = Array.of("HTTP"),
    val id: Int? = null,
    val name: String,
    val isAuthenticationRequired: Boolean,
    val parameterElementList: FieldElementList,
    var failureCodes: Array<String> = Array.empty(),
    var successCodes: Array<String> = Array.empty(),
    var response: Response? = null,
    var payloadResponse: Response? = null,
    var jobResponse: Response? = null,
    var requestBody: RequestBody? = null,
    val headers: Array<Field> = Array.empty(),
    val description: String? = null,
    val deprecated: Boolean = false,
    val deprecationNote: String? = null,
    val deprecatedSince: LocalDate? = null,
    val asyncMode: AsyncMode? = null,
    val asyncControlParameter: String? = null,
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

    enum class AsyncMode {
        ALWAYS,
        CONDITIONAL
    }

    val parameters = parameterElementList.fields

}
