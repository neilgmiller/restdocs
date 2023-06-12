package com.giffardtechnologies.restdocs.jackson.validation

import com.fasterxml.jackson.databind.module.SimpleModule

class ValidationModule : SimpleModule() {
    init {
        setDeserializerModifier(ValidatingBeanDeserializerModifier())
    }
}