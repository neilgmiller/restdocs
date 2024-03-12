package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.EnumConstant
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun <T> enumSpec(keyType: DataType.BasicKey<T>, configure: EnumSpecConfiguration<T>.() -> Unit): TypeSpec.EnumSpec<T> {
    val enumSpecBuilder = EnumSpecBuilder(keyType)
    enumSpecBuilder.configure()
    return enumSpecBuilder.build()
}

open class EnumSpecConfiguration<T> protected constructor() {

    protected val values: ArrayList<EnumConstant<T>> = ArrayList()
    private val valuesByKey: MutableMap<T, EnumConstant<T>> = LinkedHashMap()
    private val valuesByName: MutableMap<String, EnumConstant<T>> = LinkedHashMap()

    // TODO duplicate code
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

private class EnumSpecBuilder<T>(val keyType: DataType.BasicKey<T>) : EnumSpecConfiguration<T>() {

    fun build(): TypeSpec.EnumSpec<T> {
        return TypeSpec.EnumSpec(keyType, Array.ofAll(values))
    }

}