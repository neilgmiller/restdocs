package com.giffardtechnologies.restdocs.jackson

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext

internal class TrueOnNullBooleanDeserializer : YesNoBooleanDeserializer() {

    override fun getNullValue(ctxt: DeserializationContext): Boolean = true

}
