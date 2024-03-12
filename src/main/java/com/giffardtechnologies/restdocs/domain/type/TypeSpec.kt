package com.giffardtechnologies.restdocs.domain.type

import com.giffardtechnologies.restdocs.domain.Context
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Restriction
import io.vavr.collection.Array
import java.time.Instant

/**
 * @param value the value of this constant
 * @param longName a human readable name for this enum constant
 * @property description a description of the semantics of this enum constant
 */
data class EnumConstant<T>(val value: T, val longName: String, val description: String? = null)
class FlagConstant<T> {}

sealed interface BooleanRepresentation {
    object AsString: BooleanRepresentation
    object AsInteger: BooleanRepresentation
}
sealed interface DataType<T> {
    sealed interface BasicKey<T> : DataType<T> {
        fun parse(value: String): T
    }

    sealed interface UsableAsFlag<T> : DataType<T>

    object IntType: DataType<Int>, BasicKey<Int>, UsableAsFlag<Int> {
        override fun parse(value: String): Int = value.toInt()
    }

    object LongType: DataType<Long>, BasicKey<Long>, UsableAsFlag<Long> {
        override fun parse(value: String): Long = value.toLong()
    }

    object FloatType: DataType<Float>
    object DoubleType: DataType<Double>
    object StringType: DataType<String>, BasicKey<String> {
        override fun parse(value: String): String = value
    }

    object DateType: DataType<Instant>
}

sealed interface TypeSpec {
    data class DataSpec(
        val type: DataType<*>,
        val representedAs: DataType<*>? = null,
        var restrictions: Array<Restriction> = Array.empty()
    ) : TypeSpec

    data class BooleanSpec(val representedAs: BooleanRepresentation = BooleanRepresentation.AsString): TypeSpec

    sealed interface Nameable : TypeSpec
    /**
     * @param fieldElementList Used for translating objects, general case should use the list getters.
     * @see .getFieldListElements
     * @see .getFields
     */
    data class ObjectSpec(val fieldElementList: FieldElementList) : Nameable {
        val fields = fieldElementList.fields
    }
    data class EnumSpec<T>(val key: DataType.BasicKey<T>, val values: Array<EnumConstant<T>>) : Nameable
    data class TypeRefSpec(val referenceName: String, val context: Context) : TypeSpec {
        val typeRef: Lazy<NamedType<*>> = lazy { checkNotNull(context.getTypeByName(referenceName)) { "'$referenceName' does not match any type in context" } }
    }
    sealed interface CollectionSpec: TypeSpec
    data class ArraySpec(val items: TypeSpec) : CollectionSpec
    data class MapSpec<T>(val key: DataType.BasicKey<T>, val items: TypeSpec) : CollectionSpec
    data class BitSetSpec<T>(val flagType: DataType.UsableAsFlag<T>, val values: Array<FlagConstant<T>>) : TypeSpec

}

