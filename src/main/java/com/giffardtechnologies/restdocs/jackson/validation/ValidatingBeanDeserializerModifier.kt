package com.giffardtechnologies.restdocs.jackson.validation

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.type.*
import java.io.IOException

class ValidatingBeanDeserializerModifier : BeanDeserializerModifier() {
    override fun modifyDeserializer(
        config: DeserializationConfig,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(beanDesc.type, deserializer)
    }

    override fun modifyEnumDeserializer(
        config: DeserializationConfig,
        type: JavaType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyReferenceDeserializer(
        config: DeserializationConfig,
        type: ReferenceType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyArrayDeserializer(
        config: DeserializationConfig,
        type: ArrayType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyCollectionDeserializer(
        config: DeserializationConfig,
        type: CollectionType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyCollectionLikeDeserializer(
        config: DeserializationConfig,
        type: CollectionLikeType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyMapDeserializer(
        config: DeserializationConfig,
        type: MapType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyMapLikeDeserializer(
        config: DeserializationConfig,
        type: MapLikeType,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyKeyDeserializer(
        config: DeserializationConfig,
        type: JavaType,
        deserializer: KeyDeserializer
    ): KeyDeserializer {
        return object : KeyDeserializer() {
            @Throws(IOException::class)
            override fun deserializeKey(key: String, ctxt: DeserializationContext): Any {
                val deserializedKey = deserializer.deserializeKey(key, ctxt)
                if (deserializedKey is Validatable) {
                    deserializedKey.validate()
                }
                return deserializedKey
            }
        }
    }

    private fun createDelegate(type: JavaType, target: JsonDeserializer<*>): JsonDeserializer<*> {
        return ValidatingDeserializer(target)
    }
}