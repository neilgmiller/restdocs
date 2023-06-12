package com.giffardtechnologies.restdocs.jackson.validation

import com.fasterxml.jackson.databind.module.SimpleModule

class ValidationModule(validationContext: Any? = null) : SimpleModule() {
    init {
        setDeserializerModifier(ValidatingBeanDeserializerModifier(validationContext))
    }
}