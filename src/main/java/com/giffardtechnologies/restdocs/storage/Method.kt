package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement

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
)