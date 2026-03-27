package com.giffardtechnologies.restdocs.domain.type

import com.giffardtechnologies.restdocs.domain.Context
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Restriction
import io.vavr.collection.Array
import java.time.Instant

/**
 * @param value the value of this constant
 * @param longName a human-readable name for this enum constant
 * @property description a description of the semantics of this enum constant
 */
data class EnumConstant<T>(val value: T, val longName: String, val description: String? = null)
data class FlagConstant<T>(val value: T, val longName: String, val description: String? = null)

sealed interface BooleanRepresentation {
    object AsString: BooleanRepresentation
    object AsInteger: BooleanRepresentation
}

sealed interface DataType<T> {

    /** Base type for the primitive scalar types. */
    sealed interface BasicType<T> : DataType<T>

    sealed interface ReRepresentableType<T> : BasicType<T>

    sealed interface UsableAsKey<T> : BasicType<T> {
        fun parse(value: String): T
    }

    sealed interface UsableAsFlag<T> : UsableAsKey<T>

    object IntType: ReRepresentableType<Int>, UsableAsFlag<Int> {
        override fun parse(value: String): Int = value.toInt()
    }

    object LongType: ReRepresentableType<Long>, UsableAsFlag<Long> {
        override fun parse(value: String): Long = value.toLong()
    }

    object FloatType: ReRepresentableType<Float>
    object DoubleType: ReRepresentableType<Double>
    object StringType: ReRepresentableType<String>, UsableAsKey<String> {
        override fun parse(value: String): String = value
    }

    object BooleanType: BasicType<Boolean>

    object DateType: DataType<Instant>
}

sealed interface TypeSpec {
    data class BasicSpec(
        val type: DataType.ReRepresentableType<*>,
        val representedAs: DataType.BasicType<*>? = null,
        var restrictions: Array<Restriction> = Array.empty()
    ) : TypeSpec

    data class DateSpec(
        var restrictions: Array<Restriction> = Array.empty()
    ) : TypeSpec

    data class StringSpec(
        val parsedAs: DataType.BasicType<*>,
        val representedAs: DataType.BasicType<*>? = null,
    ): TypeSpec

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
    data class EnumSpec<T>(val key: DataType.UsableAsKey<T>, val values: Array<EnumConstant<T>>) : Nameable
    data class TypeRefSpec(val referenceName: String, val context: Context) : TypeSpec {
        val typeRef: Lazy<NamedType<*>> = lazy { checkNotNull(context.getTypeByName(referenceName)) { "'$referenceName' does not match any type in context" } }
    }
    sealed interface CollectionSpec: TypeSpec
    data class ArraySpec(val items: TypeSpec) : CollectionSpec
    data class MapSpec<T>(val key: DataType.UsableAsKey<T>, val items: TypeSpec) : CollectionSpec
    data class BitSetSpec<T>(val flagType: DataType.UsableAsFlag<T>, val values: Array<FlagConstant<T>>) : Nameable

}

