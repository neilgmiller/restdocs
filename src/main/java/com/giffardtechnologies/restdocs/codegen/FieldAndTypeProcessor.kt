package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.DataType.BasicKey
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.ArraySpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.MapSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName

@Suppress("unused", "MemberVisibilityCanBePrivate")
class FieldAndTypeProcessor(
    private val objectPackage: String,
    private val typeRefPackage: String,
    private val subObjectPackage: String = objectPackage,
) {

    fun createPropertySpec(
        field: Field,
        useFutureProofEnum: Boolean = true,
        objectClassName: ClassName,
        initializeCollections: Boolean = true
    ): PropertySpec {
        return createPropertySpec(field, useFutureProofEnum, initializeCollections, objectClassName) { parentClassName, subField ->
            parentClassName.nestedClass(subField.toObjectName())
        }
    }

    fun createPropertySpec(
        field: Field,
        useFutureProofEnum: Boolean = true,
        initializeCollections: Boolean = true,
        objectClassName: ClassName,
        subObjectClassNameFactory: (ClassName, Field) -> ClassName
    ): PropertySpec {
        val isNullable = (!field.isRequired && field.defaultValue == null) && !(field.type is ArraySpec || field.type is MapSpec<*>)
        val fieldBuilder = PropertySpec.builder(
            field.longName,
            getTypeName(
                field.type,
                !isNullable,
                false,
                useFutureProofEnum,
                subObjectClassNameFactory = { field -> subObjectClassNameFactory(objectClassName, field) },
                parentField = field,
            ),
        )
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(
                AnnotationSpec.builder(SerialName::class.asClassName())
                    .addMember("%S", field.name)
                    .build()
            )

        // add initializers
        val type = getEffectiveFieldType(field)

        if (!field.isRequired && field.defaultValue != null) {
            // add an initializer
            when (type) {
                is TypeSpec.DataSpec -> {
                    when(type.type) {
                        DataType.IntType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.LongType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.StringType -> fieldBuilder.initializer("%S", field.defaultValue)
                        DataType.DateType -> {}
                        DataType.DoubleType -> fieldBuilder.initializer("%L", field.defaultValue)
                        DataType.FloatType -> fieldBuilder.initializer("%L", field.defaultValue)
                    }
                }
                is ArraySpec -> fieldBuilder.initializer("listOf()")
                is TypeSpec.BitSetSpec<*> -> {}
                is TypeSpec.BooleanSpec -> {}
                is MapSpec<*> -> fieldBuilder.initializer("mapOf()")
                is TypeSpec.EnumSpec<*> -> {
                    val className = if (field.type is TypeSpec.TypeRefSpec) {
                        ClassName(typeRefPackage, field.toObjectName())
                    } else {
                        subObjectClassNameFactory(objectClassName, field)
                    }
                    if (useFutureProofEnum) {
                        // TODO convert default to value
                    } else {
                        fieldBuilder.initializer(
                            "%T.%L",
                            className,
                            convertToEnumConstantStyle(field.defaultValue)
                        )
                    }
                }
                is TypeSpec.ObjectSpec -> {}
                is TypeSpec.TypeRefSpec -> {}
            }
        } else if (initializeCollections && type is TypeSpec.CollectionSpec) {
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
            is TypeSpec.DataSpec,
            is ArraySpec,
            is TypeSpec.BitSetSpec<*>,
            is TypeSpec.BooleanSpec,
            is MapSpec<*>,
            is TypeSpec.EnumSpec<*>,
            is TypeSpec.ObjectSpec -> type
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
    ): TypeName {
        val typeName = when (typeSpec) {
            is TypeSpec.BitSetSpec<*> -> Int::class.asTypeName() // TODO()
            is TypeSpec.BooleanSpec -> Int::class.asTypeName() // TODO()
            is ArraySpec -> Int::class.asTypeName() // TODO()
            is MapSpec<*> -> Int::class.asTypeName() // TODO()
            is TypeSpec.DataSpec -> getBasicTypeName(typeSpec.type)
            is TypeSpec.EnumSpec<*> -> {
                subObjectClassNameFactory(parentField)
            }

            is TypeSpec.ObjectSpec -> {
                subObjectClassNameFactory(parentField)
            }

            is TypeSpec.TypeRefSpec -> ClassName(typeRefPackage, typeSpec.referenceName)

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

    private fun getBasicTypeName(type: DataType<*>): TypeName {
        return when (type) {
            is BasicKey<*> -> getKeyTypeName(type)
            DataType.DateType -> LocalDate::class.asTypeName()
            DataType.DoubleType -> Double::class.asTypeName()
            DataType.FloatType -> Float::class.asTypeName()
        }
    }

    private fun getKeyTypeName(key: BasicKey<*>): TypeName {
        return when (key) {
            DataType.IntType -> Int::class.asTypeName()
            DataType.LongType -> Long::class.asTypeName()
            DataType.StringType -> String::class.asTypeName()
        }
    }

}
