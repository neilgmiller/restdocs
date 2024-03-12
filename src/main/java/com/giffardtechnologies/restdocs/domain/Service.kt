package com.giffardtechnologies.restdocs.domain

import io.vavr.collection.Array

class Service(
    val description: String? = null,
    val basePath: String? = null,
    val common: Common? = null,
    val methods: Array<Method>,
) {

    data class Common(
        val headers: Array<Field> = Array.empty(),
        val parameters: Array<Field> = Array.empty(),
        var responseDataObjects: Array<DataObject> = Array.empty()
    )

}
