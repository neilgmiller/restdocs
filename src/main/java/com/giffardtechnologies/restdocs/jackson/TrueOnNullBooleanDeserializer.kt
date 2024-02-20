package com.giffardtechnologies.restdocs.jackson

import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers.BooleanDeserializer

internal class TrueOnNullBooleanDeserializer : DelegatingDeserializer(BooleanDeserializer(Boolean::class.java, true)) {

    override fun newDelegatingInstance(newDelegatee: JsonDeserializer<*>?): JsonDeserializer<*> {
        throw NotImplementedError("AFAIK we don't need this, and whatever it's supposed to do is not documented.")
    }

}

