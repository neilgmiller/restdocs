package com.giffardtechnologies.restdocs.domain

class Restriction {
    @JvmField
    var restriction: String? = null
    var value = ""
    var values: ArrayList<Any>? = null
    val hasMultipleValues: Boolean
        get() = values != null && !values!!.isEmpty()
}
