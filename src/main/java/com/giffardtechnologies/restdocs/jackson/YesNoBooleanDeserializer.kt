package com.giffardtechnologies.restdocs.jackson

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal open class YesNoBooleanDeserializer : StdDeserializer<Boolean>(Boolean::class.java) {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Boolean {
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            return when (p.string.lowercase()) {
                "yes", "true" -> true
                "no", "false" -> false
                else -> ctxt.handleWeirdStringValue(Boolean::class.java, p.string, "Expected boolean, \"yes\", or \"no\"") as Boolean
            }
        }
        return p.booleanValue
    }

}
