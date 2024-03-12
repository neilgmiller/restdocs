package com.giffardtechnologies.restdocs.domain

class Restriction(val restriction: String) {
    var value = ""
    var values: ArrayList<Any>? = null
    val hasMultipleValues: Boolean
        get() = values != null && !values!!.isEmpty()
}
