package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.vavr.isNotEmpty
import io.vavr.collection.Array

class Service(
    val description: String? = null,
    val basePath: String? = null,
    val common: Common? = null,
    private var methods: Array<Method> = Array.empty()
) {

    class Common(
        val headers: Array<Field> = Array.empty(),
        val parameters: Array<Field> = Array.empty(),
        var responseDataObjects: Array<DataObject> = Array.empty()
    ) {

        val hasHeaders: Boolean
            get() = headers.isNotEmpty()

        val hasParameters: Boolean
            get() = parameters.isNotEmpty()

        val hasResponseDataObjects: Boolean
            get() = responseDataObjects.isNotEmpty()

    }

    val hasCommon: Boolean
        get() = common != null

}
