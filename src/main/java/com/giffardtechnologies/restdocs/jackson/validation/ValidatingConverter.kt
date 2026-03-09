package com.giffardtechnologies.restdocs.jackson.validation

import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.type.TypeFactory
import tools.jackson.databind.util.Converter

class ValidatingConverter(private val type: JavaType, private val validationContext: Any?) : Converter<Any, Any> {

    override fun convert(ctxt: DeserializationContext, value: Any): Any {
        if (value is Validatable) {
            try {
                value.validate(validationContext)
            } catch (e: Exception) {
                if (e is JacksonException) throw e
                throw ctxt.instantiationException(value::class.java, e)
            }
        }
        return value
    }

    override fun convert(ctxt: SerializationContext, value: Any): Any {
        return value
    }

    override fun getInputType(typeFactory: TypeFactory): JavaType {
        return type
    }

    override fun getOutputType(typeFactory: TypeFactory): JavaType {
        return type
    }
}
