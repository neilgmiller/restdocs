package com.giffardtechnologies.restdocs.jackson.validation

import tools.jackson.databind.module.SimpleModule

class ValidationModule(validationContext: Any? = null) : SimpleModule() {
    init {
        setDeserializerModifier(ValidatingBeanDeserializerModifier(validationContext))
    }
}