package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.EnumConstant
import com.giffardtechnologies.restdocs.domain.type.FlagConstant
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun <T> bitSetSpec(keyType: DataType.UsableAsFlag<T>, configure: BitSetSpecConfiguration<T>.() -> Unit): TypeSpec.BitSetSpec<T> {
    val enumSpecBuilder = BitSetSpecBuilder(keyType)
    enumSpecBuilder.configure()
    return enumSpecBuilder.build()
}

open class BitSetSpecConfiguration<T> protected constructor() {

    protected val values: ArrayList<FlagConstant<T>> = ArrayList()
    private val valuesByKey: MutableMap<T, FlagConstant<T>> = LinkedHashMap()
    private val valuesByName: MutableMap<String, FlagConstant<T>> = LinkedHashMap()

    // TODO duplicate code
    fun value(value: T, longName: String, description: String? = null) {
        val existingEnumConstantByKey = valuesByKey[value]
        val existingEnumConstantByName = valuesByName[longName]
        val enumConstant = FlagConstant(value, longName, description)
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

private class BitSetSpecBuilder<T>(val keyType: DataType.UsableAsFlag<T>) : BitSetSpecConfiguration<T>() {

    fun build(): TypeSpec.BitSetSpec<T> {
        return TypeSpec.BitSetSpec(keyType, Array.ofAll(values))
    }

}