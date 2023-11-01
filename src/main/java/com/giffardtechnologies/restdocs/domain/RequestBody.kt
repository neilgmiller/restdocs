package com.giffardtechnologies.restdocs.domain

import com.google.gson.annotations.SerializedName

class RequestBody {
    var description: String? = null

    @SerializedName("content types")
    var contentTypes: ArrayList<String>? = null
}
