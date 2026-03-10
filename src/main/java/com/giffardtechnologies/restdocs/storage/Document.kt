package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The root document model representing a complete REST API specification.
 *
 * @property title The human-readable title of the API.
 * @property service The service definition containing methods and common definitions, or null if
 * the document only defines shared types.
 * @property enumerations Top-level named enumerations that can be referenced by fields across the
 * document.
 * @property bitsets Top-level named bitsets that can be referenced by fields across the document.
 * @property dataObjects Top-level named data objects that can be referenced by fields across the
 * document.
 */
data class Document(
        val title: String,
        val service: Service?,
        val enumerations: ArrayList<NamedEnumeration> = ArrayList(),
        val bitsets: ArrayList<NamedBitSet> = ArrayList(),
        @JsonProperty("data objects") val dataObjects: ArrayList<DataObject> = ArrayList()
)