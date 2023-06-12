package com.giffardtechnologies.restdocs.jackson.validation

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.Converter

class ValidatingConverter(private val type: JavaType) : Converter<Any, Any> {

    override fun convert(value: Any): Any {
        if (value is Validatable) {
            //JsonMappingException
            try {
                value.validate()
            } catch (e: Exception) {
                //JsonProcessingException
                throw JsonMappingException(null, e.message, e)
            }
        }
        return value
    }

    override fun getInputType(typeFactory: TypeFactory): JavaType {
        return type
    }

    override fun getOutputType(typeFactory: TypeFactory): JavaType {
        return type
    }
}