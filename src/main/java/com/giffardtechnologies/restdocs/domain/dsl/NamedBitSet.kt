package com.giffardtechnologies.restdocs.domain.dsl

import com.giffardtechnologies.restdocs.domain.NamedBitSet
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.FlagConstant
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array

fun <T> namedBitSet(name: String, keyType: DataType.UsableAsFlag<T>, configure: NamedBitSetConfiguration<T>.() -> Unit): NamedBitSet {
    val namedBitSetBuilder = NamedBitSetBuilder(name, keyType)
    namedBitSetBuilder.configure()
    return namedBitSetBuilder.build()
}

open class NamedBitSetConfiguration<T> protected constructor() {

    var description: String? = null

    protected val values: ArrayList<FlagConstant<T>> = ArrayList()
    private val valuesByKey: MutableMap<T, FlagConstant<T>> = LinkedHashMap()
    private val valuesByName: MutableMap<String, FlagConstant<T>> = LinkedHashMap()

    fun value(value: T, longName: String, description: String? = null) {
        val existingFlagConstantByKey = valuesByKey[value]
        val existingFlagConstantByName = valuesByName[longName]
        val flagConstant = FlagConstant(value, longName, description)
        if (existingFlagConstantByKey != null) {
            throw IllegalArgumentException("Attempt to add value $flagConstant, but value already exists $existingFlagConstantByKey")
        } else if (existingFlagConstantByName != null) {
            throw IllegalArgumentException("Attempt to add value $flagConstant, but value already exists $existingFlagConstantByName")
        } else {
            values.add(flagConstant)
            valuesByKey[value] = flagConstant
        }
    }

}

private class NamedBitSetBuilder<T>(private val name: String, val keyType: DataType.UsableAsFlag<T>) : NamedBitSetConfiguration<T>() {

    fun build(): NamedBitSet {
        return NamedBitSet(
            name,
            description,
            type = TypeSpec.BitSetSpec(keyType, Array.ofAll(values))
        )
    }

}