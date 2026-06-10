package com.giffardtechnologies.restdocs.jackson.validation

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.databind.*
import tools.jackson.databind.deser.SettableBeanProperty
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.jsontype.TypeDeserializer
import tools.jackson.databind.type.LogicalType
import tools.jackson.databind.util.AccessPattern
import java.io.IOException

/**
 * Base class that simplifies implementations of [ValueDeserializer]s
 * that mostly delegate functionality to another deserializer implementation
 * (possibly forming a chain of deserializers delegating functionality
 * in some cases)
 */
class ValidatingDeserializer(private val _delegatee: ValueDeserializer<*>, private val validationContext: Any?, private val warningEmitter: (String) -> Unit = {}) : StdDeserializer<Any?>(
    _delegatee.handledType()
) {

    /*
    / **********************************************************************
    / * Overridden methods for contextualization, resolving
    / **********************************************************************
     */
    override fun resolve(ctxt: DeserializationContext) {
        _delegatee.resolve(ctxt)
    }

    override fun createContextual(
        ctxt: DeserializationContext,
        property: BeanProperty?
    ): ValueDeserializer<*> {
        val vt = ctxt.constructType(_delegatee.handledType())
        val del = ctxt.handleSecondaryContextualization(
            _delegatee,
            property, vt
        )
        return if (del === _delegatee) {
            this
        } else {
            ValidatingDeserializer(del, validationContext, warningEmitter)
        }
    }

    override fun replaceDelegatee(delegatee: ValueDeserializer<*>): ValueDeserializer<*> {
        return if (delegatee === _delegatee) {
            this
        } else {
            ValidatingDeserializer(delegatee, validationContext, warningEmitter)
        }
    }

    /*
    / **********************************************************************
    / * Overridden deserialization methods
    / **********************************************************************
     */
    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any? {
        return validateAndReturn(_delegatee.deserialize(p, ctxt), ctxt)
    }

    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext, intoValue: Any?): Any? {
        @Suppress("UNCHECKED_CAST")
        return validateAndReturn((_delegatee as ValueDeserializer<Any?>).deserialize(p, ctxt, intoValue), ctxt)
    }

    @Throws(IOException::class)
    override fun deserializeWithType(
        p: JsonParser, ctxt: DeserializationContext,
        typeDeserializer: TypeDeserializer
    ): Any {
        return validateAndReturn(_delegatee.deserializeWithType(p, ctxt, typeDeserializer), ctxt)!!
    }

    private fun validateAndReturn(obj: Any?, ctxt: DeserializationContext): Any? {
        if (obj is Validatable) {
            try {
                obj.validate(validationContext, warningEmitter)
            } catch (e: Exception) {
                throw ctxt.instantiationException(obj::class.java, e)
            }
        }
        return obj
    }

    /*
    / **********************************************************************
    / * Overridden other methods
    / **********************************************************************
     */
    override fun isCachable(): Boolean {
        return _delegatee.isCachable
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean {
        return _delegatee.supportsUpdate(config)
    }

    override fun getDelegatee(): ValueDeserializer<*> {
        return _delegatee
    }

    override fun findBackReference(logicalName: String): SettableBeanProperty {
        // [databind#253]: Hope this works....
        return _delegatee.findBackReference(logicalName)
    }

    override fun getNullAccessPattern(): AccessPattern {
        return _delegatee.nullAccessPattern
    }

    @Throws(JacksonException::class)
    override fun getNullValue(ctxt: DeserializationContext): Any? {
        return _delegatee.getNullValue(ctxt)
    }

    @Throws(JacksonException::class)
    override fun getEmptyValue(ctxt: DeserializationContext): Any {
        return _delegatee.getEmptyValue(ctxt)
    }

    override fun logicalType(): LogicalType {
        return _delegatee.logicalType()
    }

    override fun getKnownPropertyNames(): Collection<Any> {
        return _delegatee.knownPropertyNames
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
