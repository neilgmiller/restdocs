package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.Field
import com.google.gson.annotations.SerializedName
import java.util.function.Consumer

class Service {
    var description: String? = null

    @SerializedName("base path")
    var basePath: String? = null
    @JvmField
    var common: Common? = null
    private var methods: ArrayList<Method>? = null
    private var mParentDocument: Document? = null
    var parentDocument: Document?
        get() = mParentDocument
        set(parentDocument) {
            mParentDocument = parentDocument
            common!!.setParentDocument(parentDocument)
            for (method in methods!!) {
                method.setParent(this)
            }
        }

    class Common {
        var headers: ArrayList<Field>? = null
        var parameters: ArrayList<Field>? = null

        @JvmField
        @SerializedName("response objects")
        var responseDataObjects: ArrayList<DataObject>? = ArrayList()
        fun hasHeaders(): Boolean {
            return headers != null && !headers!!.isEmpty()
        }

        val hasHeaders: Boolean
            get() = hasHeaders()

        fun hasParameters(): Boolean {
            return parameters != null && !parameters!!.isEmpty()
        }

        val hasParameters: Boolean
            get() = hasParameters()

        fun hasResponseDataObjects(): Boolean {
            return responseDataObjects != null && !responseDataObjects!!.isEmpty()
        }

        val hasResponseDataObjects: Boolean
            get() = hasResponseDataObjects()

        fun setParentDocument(parentDocument: Document?) {
            if (headers != null) headers!!.forEach(Consumer { field: Field -> field.parentDocument = parentDocument })
            parameters!!.forEach(Consumer { field: Field -> field.parentDocument = parentDocument })
            responseDataObjects!!.forEach(Consumer { dataObject: DataObject -> dataObject.setParent(parentDocument) })
        }
    }

    fun hasCommon(): Boolean {
        return common != null
    }

    val hasCommon: Boolean
        get() = hasCommon()

    fun getMethods(): ArrayList<Method> {
        return if (methods == null) ArrayList(0) else methods!!
    }

    fun setMethods(methods: ArrayList<Method>?) {
        this.methods = methods
    }
}
