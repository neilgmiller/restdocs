package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.type.Field

/**
 * The top-level service definition for the API.
 *
 * @property description Human-readable description of the service.
 * @property basePath The base URL path prepended to all method paths (e.g., `/api/v1`).
 * @property common Definitions shared across all methods in the service.
 * @property methods The list of API endpoints provided by this service.
 */
data class Service(
    val description: String? = null,
    @JsonProperty("base path") val basePath: String? = null,
    val common: Common? = null,
    val methods: ArrayList<Method>? = null
)

/**
 * Definitions common to all methods in a [Service].
 *
 * @property headers HTTP headers that apply to every method in the service.
 * @property parameters Query or path parameters that apply to every method in the service.
 * @property responseDataObjects Shared data objects used in method responses.
 * @property enums Enumerations that are scoped to the service.
 */
class Common(
    val headers: ArrayList<Field>? = null,
    val parameters: ArrayList<Field>? = null,
    @JsonProperty("response objects")
    val responseDataObjects: ArrayList<DataObject> = ArrayList(),
    val enums: ArrayList<NamedEnumeration>? = null,
)