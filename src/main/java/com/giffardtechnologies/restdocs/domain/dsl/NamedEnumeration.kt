package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.EnumConstant
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun <T> namedEnumeration(name: String, keyType: DataType.BasicKey<T>, configure: NamedEnumerationConfiguration<T>.() -> Unit): NamedEnumeration {
    val namedEnumerationBuilder = NamedEnumerationBuilder(name, keyType)
    namedEnumerationBuilder.configure()
    return namedEnumerationBuilder.build()
}

open class NamedEnumerationConfiguration<T> protected constructor() {

    var description: String? = null

    protected val values: ArrayList<EnumConstant<T>> = ArrayList()
    private val valuesByKey: MutableMap<T, EnumConstant<T>> = LinkedHashMap()
    private val valuesByName: MutableMap<String, EnumConstant<T>> = LinkedHashMap()

    fun value(value: T, longName: String, description: String? = null) {
        val existingEnumConstantByKey = valuesByKey[value]
        val existingEnumConstantByName = valuesByName[longName]
        val enumConstant = EnumConstant(value, longName, description)
        if (existingEnumConstantByKey != null) {
            throw IllegalArgumentException("Attempt to add value $enumConstant, but value already exists $existingEnumConstantByKey")
        } else if (existingEnumConstantByName != null) {
            throw IllegalArgumentException("Attempt to add value $enumConstant, but value already exists $existingEnumConstantByName")
        } else {
            values.add(enumConstant)
            valuesByKey[value] = enumConstant
        }
    }

}

private class NamedEnumerationBuilder<T>(private val name: String, val keyType: DataType.BasicKey<T>) : NamedEnumerationConfiguration<T>() {

    fun build(): NamedEnumeration {
        return NamedEnumeration(
            name,
            description,
            type = TypeSpec.EnumSpec(keyType, Array.ofAll(values))
        )
    }

}