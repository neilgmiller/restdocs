package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.Field
import com.google.gson.annotations.SerializedName

class Method {
    enum class HTTPMethod {
        GET,
        PUT,
        POST,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE,
        CONNECT
    }

    var method: HTTPMethod? = null
    @JvmField
    var path: String? = null

    @SerializedName("protocols allowed")
    var protocolsAllowed = ArrayList<String>()
    @JvmField
    var id: Int? = null
    @JvmField
    var name = ""
    @JvmField
    var description: String? = ""

    @SerializedName("authentication required")
    var isAuthenticationRequired = true
    var headers: ArrayList<Field>? = ArrayList()
    private val parameters = FieldElementList()

    @SerializedName("request body")
    var requestBody: RequestBody? = null
    @JvmField
    var response: Response? = null

    @SerializedName("successful codes")
    var successCodes = ArrayList<String>()

    @SerializedName("failure codes")
    var failureCodes = ArrayList<String>()

    init {
        protocolsAllowed.add("HTTP")
    }

    fun setParent(service: Service) {
        if (response != null) {
            response.setParentDocument(service.parentDocument)
        }
        parameters.setParentDocument(service.parentDocument)
    }

    val methodString: String
        get() = if (method == null) "null" else method!!.name
    val hasID: Boolean
        get() = id != null
    val hasDescription: Boolean
        get() = description != null && !description!!.isEmpty()

    fun getIsAuthenticationRequired(): Boolean {
        return isAuthenticationRequired
    }

    fun hasHeaders(): Boolean {
        return headers != null && !headers!!.isEmpty()
    }

    val hasHeaders: Boolean
        get() = hasHeaders()
    val hasParameters: Boolean
        get() = parameters.hasFields

    fun getParameters(): ArrayList<Field>? {
        return parameters.getFields()
    }

    fun setParameters(parameters: ArrayList<Field?>?) {
        this.parameters.setFields(parameters)
    }

    val hasRequestBody: Boolean
        get() = requestBody != null
}
