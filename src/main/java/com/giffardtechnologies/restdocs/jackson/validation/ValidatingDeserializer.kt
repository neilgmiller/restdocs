package com.giffardtechnologies.restdocs.jackson.validation

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer
import com.fasterxml.jackson.databind.deser.SettableBeanProperty
import com.fasterxml.jackson.databind.deser.impl.ObjectIdReader
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer
import com.fasterxml.jackson.databind.type.LogicalType
import com.fasterxml.jackson.databind.util.AccessPattern
import java.io.IOException

/**
 * Base class that simplifies implementations of [JsonDeserializer]s
 * that mostly delegate functionality to another deserializer implementation
 * (possibly forming a chaing of deserializers delegating functionality
 * in some cases)
 *
 * @since 2.1
 */
class ValidatingDeserializer(private val _delegatee: JsonDeserializer<*>, private val validationContext: Any?) : StdDeserializer<Any?>(
    _delegatee.handledType()
), ContextualDeserializer, ResolvableDeserializer {

    /*
    / **********************************************************************
    / * Overridden methods for contextualization, resolving
    / **********************************************************************
     */
    @Throws(JsonMappingException::class)
    override fun resolve(ctxt: DeserializationContext) {
        if (_delegatee is ResolvableDeserializer) {
            (_delegatee as ResolvableDeserializer).resolve(ctxt)
        }
    }

    @Throws(JsonMappingException::class)
    override fun createContextual(
        ctxt: DeserializationContext,
        property: BeanProperty?
    ): JsonDeserializer<*> {
        val vt = ctxt.constructType(_delegatee.handledType())
        val del = ctxt.handleSecondaryContextualization(
            _delegatee,
            property, vt
        )
        return if (del === _delegatee) {
            this
        } else {
            ValidatingDeserializer(del, validationContext)
        }
    }

    override fun replaceDelegatee(delegatee: JsonDeserializer<*>): JsonDeserializer<*> {
        return if (delegatee === _delegatee) {
            this
        } else {
            ValidatingDeserializer(delegatee, validationContext)
        }
    }

    /*
    / **********************************************************************
    / * Overridden deserialization methods
    / **********************************************************************
     */
    @Throws(IOException::class)
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): Any? {
        val deserializedObject: Any = _delegatee.deserialize(p, ctxt) ?: return null
        if (deserializedObject is Validatable) {
            try {
                deserializedObject.validate(validationContext)
            } catch (e: Exception) {
                throw JsonMappingException(ctxt.parser, e.message, e)
            }
        }
        return deserializedObject
    }

    @Throws(IOException::class)
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
        intoValue: Any?
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        return (_delegatee as JsonDeserializer<Any?>).deserialize(p, ctxt, intoValue)
    }

    @Throws(IOException::class)
    override fun deserializeWithType(
        p: JsonParser, ctxt: DeserializationContext,
        typeDeserializer: TypeDeserializer
    ): Any {
        return _delegatee.deserializeWithType(p, ctxt, typeDeserializer)
    }

    /*
    / **********************************************************************
    / * Overridden other methods
    / **********************************************************************
     */
    override fun isCachable(): Boolean {
        return _delegatee.isCachable
    }

    // since 2.9
    override fun supportsUpdate(config: DeserializationConfig): Boolean {
        return _delegatee.supportsUpdate(config)
    }

    override fun getDelegatee(): JsonDeserializer<*> {
        return _delegatee
    }

    override fun findBackReference(logicalName: String): SettableBeanProperty {
        // [databind#253]: Hope this works....
        return _delegatee.findBackReference(logicalName)
    }

    override fun getNullAccessPattern(): AccessPattern {
        return _delegatee.nullAccessPattern
    }

    @Throws(JsonMappingException::class)
    override fun getNullValue(ctxt: DeserializationContext): Any? {
        return _delegatee.getNullValue(ctxt)
    }

    @Throws(JsonMappingException::class)
    override fun getEmptyValue(ctxt: DeserializationContext): Any {
        return _delegatee.getEmptyValue(ctxt)
    }

    // since 2.12
    override fun logicalType(): LogicalType {
        return _delegatee.logicalType()
    }

    override fun getKnownPropertyNames(): Collection<Any> {
        return _delegatee.knownPropertyNames
    }

    override fun getObjectIdReader(): ObjectIdReader? {
        return _delegatee.objectIdReader
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}