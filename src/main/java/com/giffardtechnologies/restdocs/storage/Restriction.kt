package com.giffardtechnologies.restdocs.storage

class Restriction(val restriction: String) {
    val value = ""
    val values: ArrayList<Any>? = null
    val hasMultipleValues: Boolean
        get() = !values.isNullOrEmpty()
}