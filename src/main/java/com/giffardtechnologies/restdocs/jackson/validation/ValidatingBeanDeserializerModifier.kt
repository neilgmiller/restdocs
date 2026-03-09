package com.giffardtechnologies.restdocs.jackson.validation

import tools.jackson.databind.*
import tools.jackson.databind.deser.ValueDeserializerModifier
import tools.jackson.databind.type.*
import java.io.IOException

class ValidatingBeanDeserializerModifier(private val validationContext: Any?) : ValueDeserializerModifier() {
    override fun modifyDeserializer(
        config: DeserializationConfig,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(beanDescRef.get().type, deserializer)
    }

    override fun modifyEnumDeserializer(
        config: DeserializationConfig,
        type: JavaType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyReferenceDeserializer(
        config: DeserializationConfig,
        type: ReferenceType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyArrayDeserializer(
        config: DeserializationConfig,
        valueType: ArrayType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(valueType, deserializer)
    }

    override fun modifyCollectionDeserializer(
        config: DeserializationConfig,
        type: CollectionType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyCollectionLikeDeserializer(
        config: DeserializationConfig,
        type: CollectionLikeType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyMapDeserializer(
        config: DeserializationConfig,
        type: MapType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
        return createDelegate(type, deserializer)
    }

    override fun modifyMapLikeDeserializer(
        config: DeserializationConfig,
        type: MapLikeType,
        beanDescRef: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>
    ): ValueDeserializer<*> {
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
                    deserializedKey.validate(validationContext)
                }
                return deserializedKey
            }
        }
    }

    private fun createDelegate(type: JavaType, target: ValueDeserializer<*>): ValueDeserializer<*> {
        return ValidatingDeserializer(target, validationContext)
    }
}
