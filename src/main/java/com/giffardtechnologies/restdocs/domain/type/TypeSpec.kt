package com.giffardtechnologies.restdocs.domain.type

import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.FieldListElement
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
    sealed interface BasicKey<T> : DataType<T>
    sealed interface UsableAsFlag<T> : DataType<T>

    object IntType: DataType<Int>, BasicKey<Int>, UsableAsFlag<Int>
    object LongType: DataType<Long>, BasicKey<Long>, UsableAsFlag<Long>
    object FloatType: DataType<Float>
    object DoubleType: DataType<Double>
    object StringType: DataType<String>, BasicKey<String>
    data class BooleanType(val representedAs: BooleanRepresentation): DataType<Boolean>
    object DateType: DataType<Instant>
}

sealed interface TypeSpec {
    data class DataSpec(val type: DataType<*>, var restrictions: Array<Restriction> = Array.empty()) : TypeSpec
    sealed interface Nameable : TypeSpec
    /**
     * @param fieldElementList Used for translating objects, general case should use the list getters.
     * @see .getFieldListElements
     * @see .getFields
     */
    data class ObjectSpec(val fieldElementList: FieldElementList) : Nameable
    data class EnumSpec<T>(val key: DataType.BasicKey<T>, val values: Array<EnumConstant<T>>) : Nameable
    data class TypeRefSpec(val typeRef: NamedType) : TypeSpec
    data class ArraySpec(val items: TypeSpec) : TypeSpec
    data class MapSpec<T>(val key: DataType.BasicKey<T>, val items: TypeSpec) : TypeSpec
    data class BitSetSpec<T>(val flagType: DataType.UsableAsFlag<T>, val values: Array<FlagConstant<T>>) : TypeSpec

//    fun setParentDocument(parentDocument: Document?) {
//        fieldElementList.setParentDocument(parentDocument)
//        if (items != null) {
//            items!!.setParentDocument(parentDocument)
//        }
//    }

    // For velocity
    fun getIsTypeRef(): Boolean {
        return isTypeRef()
    }

    fun isTypeRef(): Boolean {
        return this is TypeRefSpec
    }

    fun hasEnumValues(): Boolean {
        return this is EnumSpec<*>
    }

    val hasEnumValues: Boolean
        get() = hasEnumValues()

    fun hasFields(): Boolean {
        return this is ObjectSpec && fieldElementList.hasFields()
    }

    val hasFields: Boolean
        get() = hasFields()

    val fields: Array<Field>
        get() = if (this is ObjectSpec) {
            fieldElementList.fields
            TODO("Needs to be convert to Vavr")
        } else {
            Array.empty()
        }

    val hasRestrictions: Boolean
        get() = this is DataSpec && !restrictions.isEmpty
}

data class Field(
    val name: String,
    val longName: String,
    val type: TypeSpec,
    val description: String? = null,
    val defaultValue: String? = null,
    val isRequired: Boolean = true,
    val parent: NamedType? = null,
    val sampleValues: Array<String> = Array.empty(),
) : FieldListElement
{

    fun hasDefaultValue(): Boolean {
        return defaultValue != null
    }

    // for velocity templating
    val hasDefaultValue: Boolean
        get() = hasDefaultValue()

}