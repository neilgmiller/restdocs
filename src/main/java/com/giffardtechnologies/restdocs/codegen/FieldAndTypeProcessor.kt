package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.NamedBitSet
import com.giffardtechnologies.restdocs.domain.type.BooleanRepresentation
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.DataType.UsableAsKey
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.ArraySpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.MapSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused", "MemberVisibilityCanBePrivate")
class FieldAndTypeProcessor(
    private val objectPackage: String,
    private val typeRefPackage: String,
    private val subObjectPackage: String = objectPackage,
    private val classifier: DataObjectUsageClassifier? = null,
    private val requestsDtoPackage: String? = null,
) {

    fun createPropertySpec(
        field: Field,
        useFutureProofEnum: Boolean = true,
        initializeCollections: Boolean = true,
        initializeWithDefault: Boolean = true,
        objectClassName: ClassName,
        subObjectClassNameFactory: (ClassName, Field) -> ClassName = { parentClassName, subField ->
            parentClassName.nestedClass(subField.toObjectName())
        }
    ): PropertySpec {
        val isNullable = (!field.isRequired && (field.defaultValue == null || !initializeWithDefault)) && !((field.type is ArraySpec || field.type is MapSpec<*>) && initializeCollections)
        val fieldBuilder = PropertySpec.builder(
            name = field.toPropertyName(),
            type = getTypeName(
                field.type,
                !isNullable,
                false,
                useFutureProofEnum,
                subObjectClassNameFactory = { soField -> subObjectClassNameFactory(objectClassName, soField) },
                parentField = field,
                parameterContext = !initializeWithDefault,
            ),
        )
            .addAnnotation(
                AnnotationSpec.builder(SerialName::class.asClassName())
                    .addMember("%S", field.name)
                    .build()
            )

        if (field.type is TypeSpec.BooleanSpec && field.type.representedAs is BooleanRepresentation.AsInteger) {
            fieldBuilder.addAnnotation(
                AnnotationSpec.builder(Serializable::class).addMember("with = %T::class", ClassName("com.allego.api.client.support.serialization", "BooleanIntSerializer")).build()
            )
        }

        if (field.type is TypeSpec.BasicSpec && field.type.type == DataType.BitType) {
            fieldBuilder.addAnnotation(
                AnnotationSpec.builder(Serializable::class).addMember("with = %T::class", ClassName("com.allego.api.client.support.serialization", "BitSerializer")).build()
            )
        }

        if (field.type is TypeSpec.StringSpec) {
            var serializerName = when (field.type.parsedAs) {
                DataType.IntType -> "StringToInt"
                DataType.LongType -> "StringToLong"
                DataType.FloatType -> "StringToFloat"
                DataType.DoubleType -> "StringToDouble"
                DataType.BooleanType -> "StringToBoolean"
                DataType.BitType -> "StringToBit"
                DataType.StringType -> TODO("Fix type hierarchy, so parsing a string a a isn't allowed")
            }
            serializerName += when (field.type.representedAs) {
                DataType.BooleanType -> "ToBoolean"
                DataType.DoubleType -> "ToDouble"
                DataType.FloatType -> "ToFloat"
                DataType.IntType -> "ToInt"
                DataType.LongType -> "ToLong"
                DataType.StringType -> "ToString"
                DataType.BitType -> "ToBit"
                null -> ""
            }
            serializerName += "Serializer"
            fieldBuilder.addAnnotation(
                AnnotationSpec.builder(Serializable::class)
                    .addMember("with = %T::class", ClassName("com.allego.api.client.support.serialization", serializerName))
                    .build()
            )
        }

        // add initializers
        val type = getEffectiveFieldType(field)

        if (initializeWithDefault && !field.isRequired && field.defaultValue != null) {
            // add an initializer
            when (type) {
                is TypeSpec.BasicSpec -> {
                    when(type.type) {
                        DataType.IntType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.LongType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.StringType -> fieldBuilder.initializer("%S", field.defaultValue)
                        DataType.DoubleType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.FloatType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.BooleanType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.BitType -> fieldBuilder.initializer("%L", field.defaultValue)
                    }
                }
                is ArraySpec -> fieldBuilder.initializer("listOf()")
                is TypeSpec.BitSetSpec<*> -> {}
                is TypeSpec.BooleanSpec -> {
                    when(type.representedAs) {
                        BooleanRepresentation.AsInteger -> {
                            val defaultBoolean = if (field.defaultValue == "0") "false" else "true"
                            fieldBuilder.initializer("%L", defaultBoolean)
                        }
                        BooleanRepresentation.AsString -> {
                            fieldBuilder.initializer("%L", field.defaultValue)
                        }
                    }
                }
                is MapSpec<*> -> fieldBuilder.initializer("mapOf()")
                is TypeSpec.EnumSpec<*> -> {
                    val className = if (field.type is TypeSpec.TypeRefSpec) {
                        ClassName(typeRefPackage, field.toObjectName())
                    } else {
                        subObjectClassNameFactory(objectClassName, field)
                    }
                    if (field.defaultValue.matches(Regex("-?\\d+"))) {
                        // TODO convert numeric to name
                    } else {
                        fieldBuilder.initializer(
                            "%T.%L",
                            className,
                            fieldNameToClassStyle(field.defaultValue)
                        )
                    }
                }
                is TypeSpec.DateSpec -> error("Default value '${field.defaultValue}' for DateSpec field '${field.name}' is not supported")
                is TypeSpec.ObjectSpec -> error("Default value '${field.defaultValue}' for ObjectSpec field '${field.name}' is not supported")
                is TypeSpec.TypeRefSpec -> error("Default value '${field.defaultValue}' for TypeRefSpec field '${field.name}' is not supported")
                is TypeSpec.StringSpec -> fieldBuilder.initializer("%S", field.defaultValue)
            }
        } else if (initializeWithDefault && initializeCollections && type is TypeSpec.CollectionSpec && !field.isRequired) {
            when (type) {
                is ArraySpec -> fieldBuilder.initializer("listOf()")
                is MapSpec<*> -> fieldBuilder.initializer("mapOf()")
            }
        } else if (isNullable) {
            fieldBuilder.initializer("null")
        }
        return fieldBuilder.build()
    }


    fun getFieldInnerClassName(dataObject: DataObject, field: Field): String {
        require(field.type !is TypeSpec.TypeRefSpec) { "Passed fields cannot be a TypeRef" }
        // TODO: 2/10/21 figure out override for this variable (maybe, this is old)
        return field.toClassNameStyle(asInner = true)
    }

    fun getEffectiveFieldType(field: Field): TypeSpec {
        return when(val type = field.type) {
            is TypeSpec.TypeRefSpec -> type.typeRef.value.type
            is TypeSpec.BasicSpec,
            is ArraySpec,
            is TypeSpec.BitSetSpec<*>,
            is TypeSpec.BooleanSpec,
            is TypeSpec.DateSpec,
            is MapSpec<*>,
            is TypeSpec.EnumSpec<*>,
            is TypeSpec.ObjectSpec -> type
            is TypeSpec.StringSpec -> {
                if (type.representedAs == null) {
                    when (type.parsedAs) {
                        is DataType.ReRepresentableType -> TypeSpec.BasicSpec(type.parsedAs)
                        is DataType.BooleanType -> TypeSpec.BooleanSpec()
//                    null -> TypeSpec.BasicSpec(DataType.StringType)
                    }
                } else {
                    when (type.representedAs) {
                        is DataType.ReRepresentableType -> TypeSpec.BasicSpec(type.representedAs)
                        DataType.BooleanType -> TypeSpec.BooleanSpec(BooleanRepresentation.AsInteger)
                        DataType.StringType -> TODO("back to string, really??")
                    }
                }
            }
        }
    }

//    private fun getTypeName(typeSpec: TypeSpec, required: Boolean): TypeName {
//        return getTypeName(typeSpec, required, false)
//    }
//
//    fun getTypeName(
//        typeSpec: TypeSpec,
//        required: Boolean,
//        convertIntBoolean: Boolean
//    ): TypeName {
//        return getTypeName(typeSpec, required, convertIntBoolean, true)
//    }
//
//    fun getTypeName(
//        typeSpec: TypeSpec,
//        required: Boolean,
//        convertIntBoolean: Boolean,
//        futureProofEnum: Boolean
//    ): TypeName {
//        return getTypeName(typeSpec, required, convertIntBoolean, futureProofEnum, null)
//    }
//
//    fun getTypeName(
//        typeSpec: TypeSpec,
//        required: Boolean,
//        convertIntBoolean: Boolean,
//        objectTypeName: TypeName?
//    ): TypeName {
//        return getTypeName(typeSpec, required, convertIntBoolean, true, objectTypeName)
//    }

    fun getTypeName(
        typeSpec: TypeSpec,
        required: Boolean,
        convertIntBoolean: Boolean = false,
        futureProofEnum: Boolean = true,
        subObjectClassNameFactory: (Field) -> ClassName,
        parentField: Field,
        parameterContext: Boolean = false,
    ): TypeName {
        val typeName: TypeName = when (typeSpec) {
            is TypeSpec.BitSetSpec<*> -> {
                val setClass = when (typeSpec.flagType) {
                    DataType.IntType -> ClassName("com.allego.api.client.support.bitset", "IntBitSet")
                    DataType.LongType -> ClassName("com.allego.api.client.support.bitset", "BitSet")
                }
                setClass.parameterizedBy(subObjectClassNameFactory(parentField))
            }
            is TypeSpec.BooleanSpec -> Boolean::class.asTypeName()
            is TypeSpec.DateSpec -> LocalDate::class.asTypeName()
            is ArraySpec -> {
                if (typeSpec.items is TypeSpec.BooleanSpec) {
                    throw IllegalArgumentException("Boolean not supported in array type")
                }
                List::class.asClassName().parameterizedBy(
                    getTypeName(
                        typeSpec.items,
                        required = true,
                        convertIntBoolean,
                        futureProofEnum,
                        subObjectClassNameFactory,
                        parentField,
                        parameterContext,
                    )
                )
            }
            is MapSpec<*> -> {
                if (typeSpec.items is TypeSpec.BooleanSpec) {
                    throw IllegalArgumentException("Boolean not supported in map type")
                }
                Map::class.asClassName().parameterizedBy(
                    getBasicTypeName(typeSpec.key),
                    getTypeName(
                        typeSpec.items,
                        required = true,
                        convertIntBoolean,
                        futureProofEnum,
                        subObjectClassNameFactory,
                        parentField,
                        parameterContext,
                    )
                )
            }
            is TypeSpec.BasicSpec -> getBasicTypeName(typeSpec.type)
            is TypeSpec.EnumSpec<*> -> {
                subObjectClassNameFactory(parentField)
            }

            is TypeSpec.ObjectSpec -> {
                subObjectClassNameFactory(parentField)
            }

            is TypeSpec.TypeRefSpec -> {
                val referencedType = typeSpec.typeRef.value
                if (referencedType is NamedBitSet) {
                    val setClass = when(referencedType.type.flagType) {
                        DataType.IntType -> ClassName("com.allego.api.client.support.bitset", "IntBitSet")
                        DataType.LongType -> ClassName("com.allego.api.client.support.bitset", "BitSet")
                    }
                    setClass.parameterizedBy(ClassName(typeRefPackage, typeSpec.referenceName))
                } else if (parameterContext && referencedType is DataObject) {
                    val classification = classifier?.classify(typeSpec.referenceName)
                    if (classification == DataObjectClassification.Mixed || classification == DataObjectClassification.ParameterOnly) {
                        ClassName(requireNotNull(requestsDtoPackage), typeSpec.referenceName + "Input")
                    } else {
                        ClassName(typeRefPackage, typeSpec.referenceName)
                    }
                } else {
                    ClassName(typeRefPackage, typeSpec.referenceName)
                }
            }
            is TypeSpec.StringSpec -> {
//                typeSpec.parsedAs?.let { getBasicTypeName(typeSpec.parsedAs) } ?: String::class.asTypeName()
                if (typeSpec.representedAs == null) {
                    getBasicTypeName(typeSpec.parsedAs)
                } else {
                    getBasicTypeName(typeSpec.representedAs)
                }
            }

//            DATE -> return ClassName.get(LocalDate::class.java)
//            COLLECTION -> return ParameterizedTypeName.get(
//                ClassName.get(MutableMap::class.java),
//                getKeyTypeName(typeSpec.key),
//                getTypeName(
//                    typeSpec.items,
//                    false,
//                    convertIntBoolean,
//                    futureProofEnum,
//                    objectTypeName
//                )
//            )
//
//            ARRAY ->                    // pass required false, since we can't use primitives
//                return ParameterizedTypeName.get(
//                    ClassName.get(MutableList::class.java),
//                    getTypeName(
//                        typeSpec.items,
//                        false,
//                        convertIntBoolean,
//                        futureProofEnum,
//                        objectTypeName
//                    )
//                )
//
//            BITSET -> {
//                val bitsetName: TypeName
//                bitsetName = objectTypeName
//                    ?: if (typeSpec is Field) {
//                        val field = typeSpec as Field
//                        ClassName.get(
//                            subObjectPackage,
//                            mJavaGenerator.mJavaTool.fieldToClassStyle(field)
//                        )
//                    } else {
//                        throw IllegalStateException("Raw bitflag type specified, cannot generate name.")
//                    }
//                return getBasicTypeName(typeSpec.flagType.type, required)
//            }

        }

        return if (required) {
            typeName
        } else {
            typeName.copy(nullable = true)
        }
    }

    /**
     * Maps a scalar (non-object, non-collection) [TypeSpec] to its Kotlin [ClassName].
     * Returns null for types that need a generated body or a name factory (ObjectSpec,
     * TypeRefSpec, collections, enums, bitsets) — those are handled elsewhere.
     */
    fun getScalarTypeName(typeSpec: TypeSpec): ClassName? = when (typeSpec) {
        is TypeSpec.BasicSpec -> getBasicTypeName(typeSpec.type) as ClassName
        is TypeSpec.BooleanSpec -> Boolean::class.asClassName()
        is TypeSpec.DateSpec -> LocalDate::class.asClassName()
        is TypeSpec.StringSpec -> (
            if (typeSpec.representedAs == null) getBasicTypeName(typeSpec.parsedAs)
            else getBasicTypeName(typeSpec.representedAs)
        ) as ClassName
        else -> null
    }

    private fun getBasicTypeName(type: DataType<*>): TypeName {
        return when (type) {
            is UsableAsKey<*> -> getKeyTypeName(type)
            DataType.DateType -> LocalDate::class.asTypeName()
            DataType.DoubleType -> Double::class.asTypeName()
            DataType.FloatType -> Float::class.asTypeName()
            DataType.BooleanType -> Boolean::class.asTypeName()
            DataType.BitType -> Int::class.asTypeName()
        }
    }

    private fun getKeyTypeName(key: UsableAsKey<*>): TypeName {
        return when (key) {
            DataType.IntType -> Int::class.asTypeName()
            DataType.LongType -> Long::class.asTypeName()
            DataType.StringType -> String::class.asTypeName()
        }
    }

}
