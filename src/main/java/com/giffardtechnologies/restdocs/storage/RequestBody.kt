package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Describes the body that a client sends with an HTTP request.
 *
 * @property description Human-readable description of the expected request body.
 * @property contentTypes The MIME types accepted for the request body (e.g., `"application/json"`).
 */
class RequestBody {
    val description: String? = null

    @field:JsonProperty("content types")
    val contentTypes: ArrayList<String>? = null
}