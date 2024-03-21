package com.giffardtechnologies.restdocs.codegen

import com.allego.api.client.support.futureproof.EnumID
import com.allego.api.client.support.futureproof.ImmutableEnumID
import com.giffardtechnologies.meter.file
import com.allego.util.futureproofenum.FutureProof
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.EnumSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.Serializable
import java.io.File

/**
 *
 * @param enumPackage        the package where the enum should be placed
 */
class EnumProcessor(
    private val codeDirectory: File,
    private val enumPackage: String,
) {

    private val toFunctions = mutableListOf<FunSpec>()
    private val serializers = mutableListOf<TypeSpec>()

    /**
     * @param namedEnumeration   the enumeration to process
     * @param useFutureProofEnum whether the enum should be generating using [FutureProof] annotations
     */
    fun processEnum(
        namedEnumeration: NamedEnumeration,
        useFutureProofEnum: Boolean = true
    ) {
        val typeSpec: TypeSpec = processEnumToTypeSpec(namedEnumeration, useFutureProofEnum)
        val fileSpec = file(enumPackage, namedEnumeration.typeName) {
            addType(typeSpec)
        }

        fileSpec.writeTo(codeDirectory)
    }

    private fun processEnumToTypeSpec(
        namedEnumeration: NamedEnumeration,
        useFutureProofEnum: Boolean
    ): TypeSpec {
        val enumClassName = ClassName(enumPackage, namedEnumeration.typeName)
        return processEnumToTypeSpec(enumClassName, namedEnumeration.type, useFutureProofEnum, isNamed = true)
    }

    fun processEnumToTypeSpec(
        enumClassName: ClassName,
        enumSpec: EnumSpec<*>,
        useFutureProofEnum: Boolean,
        isNamed: Boolean = false,
    ): TypeSpec {
        return if (useFutureProofEnum) {
            processEnumToSealedTypeSpec(enumClassName, enumSpec, true)
        } else {
            processEnumToSealedTypeSpec(enumClassName, enumSpec, false)
//            TODO("what approach")
//            val builder = TypeSpec.enumBuilder(enumClassName).addModifiers(KModifier.PUBLIC)
//            val enumKeyTypeClass = when (enumSpec.key) {
//                DataType.IntType -> {
//                    builder.addSuperinterface(IntId::class.asClassName())
//                    Int::class
//                }
//
//                DataType.LongType -> {
//                    builder.addSuperinterface(LongId::class.asClassName())
//                    Long::class
//                }
//
//                DataType.StringType -> {
//                    builder.addSuperinterface(StringId::class.asClassName())
//                    String::class
//                }
//            }
//            for (enumConstant in enumSpec.values) {
//                var enumConstantValue = enumConstant.value
//                if (enumSpec.key === DataType.StringType) {
//                    enumConstantValue = "\"" + enumConstantValue + "\""
//                }
//                builder.addEnumConstant(
//                    convertToEnumConstantStyle(enumConstant.longName)
//                )
//            }
            //            val idFieldBuilder = FieldSpec.builder(enumKeyTypeClass, "id")
            //                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            //            builder.addField(idFieldBuilder.build())
            //            builder.addMethod(
            //                MethodSpec.constructorBuilder()
            //                    .addParameter(enumKeyTypeClass, "id")
            //                    .addStatement("\$N = \$N", "this.id", "id")
            //                    .build()
            //            )
            //            builder.addMethod(
            //                MethodSpec.methodBuilder("getId")
            //                    .returns(enumKeyTypeClass)
            //                    .addModifiers(Modifier.PUBLIC)
            //                    .addStatement("return \$N", "id")
            //                    .build()
            //            )
//            return builder.build()
        }
    }

    private fun processEnumToSealedTypeSpec(
        enumClassName: ClassName,
        enumSpec: EnumSpec<*>,
        useFutureProofEnum: Boolean
    ): TypeSpec {
        val enumKeyTypeClass = when (enumSpec.key) {
            DataType.IntType -> Int::class
            DataType.LongType -> Long::class
            DataType.StringType -> String::class
        }

        val serializerClassName =
            ClassName("$enumPackage.serialization", "${enumClassName.buildNamePrefix()}Serializer")

        val builder = TypeSpec.classBuilder(enumClassName).addModifiers(KModifier.SEALED)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("id", enumKeyTypeClass).build())
            .addSuperinterface(
                superinterface = EnumID::class.asClassName().parameterizedBy(enumKeyTypeClass.asClassName()),
                delegate = CodeBlock.of("%T(id)", ImmutableEnumID::class.asClassName())
            )
            .addAnnotation(
                AnnotationSpec.builder(Serializable::class).addMember("with = %T::class", serializerClassName).build()
            )

        val mapperFunctionCodeBlockBuilder = CodeBlock.builder().beginControlFlow("return when (this) {")

        val unknownClassName = enumClassName.nestedClass("UnknownValue")
        if (useFutureProofEnum) {
            builder.addType(
                TypeSpec.classBuilder(unknownClassName)
                    .primaryConstructor(FunSpec.constructorBuilder().addParameter("id", enumKeyTypeClass).build())
                    .superclass(enumClassName)
                    .addSuperclassConstructorParameter(CodeBlock.of("%N", "id")).build()
            )
        }

        for (enumConstant in enumSpec.values) {
            var enumConstantValue = enumConstant.value
            if (enumSpec.key === DataType.StringType) {
                enumConstantValue = "\"" + enumConstantValue + "\""
            }
            val enumConstantClassName = fieldNameToClassStyle(enumConstant.longName)
            builder.addType(
                TypeSpec.objectBuilder(enumConstantClassName).addModifiers(KModifier.DATA).superclass(enumClassName)
                    .addSuperclassConstructorParameter(CodeBlock.of("%L", enumConstantValue)).build()
            )
            mapperFunctionCodeBlockBuilder.addStatement(
                "%L -> %T",
                enumConstantValue,
                enumClassName.nestedClass(enumConstantClassName)
            )
        }
        if (useFutureProofEnum) {
            mapperFunctionCodeBlockBuilder.addStatement("else -> %T(this)", unknownClassName)
        } else {
            mapperFunctionCodeBlockBuilder.addStatement(
                "else -> throw IllegalArgumentException(\"Unsupported value: '\$this'\")"
            )
            // IllegalArgumentException does not need an include
        }
        mapperFunctionCodeBlockBuilder.endControlFlow()

        val mapperFunctionName = "to${enumClassName.buildNamePrefix()}"
        toFunctions.add(
            FunSpec.builder(mapperFunctionName)
                .receiver(enumKeyTypeClass)
                .returns(enumClassName)
                .addCode(mapperFunctionCodeBlockBuilder.build()).build()
        )

        serializers.add(
            TypeSpec.classBuilder(serializerClassName)
                .superclass(
                    ClassName(
                        "com.allego.api.client.support.futureproof",
                        enumKeyTypeClass.simpleName + "EnumIDSerializer"
                    ).parameterizedBy(enumClassName)
                )
                .addSuperclassConstructorParameter("%S", enumClassName.simpleName)
                .addSuperclassConstructorParameter(
                    "%L::%T",
                    enumKeyTypeClass.asClassName().simpleName,
                    ClassName("$enumPackage.support", mapperFunctionName)
                )
                .build()
        )

        return builder.build()
    }

    fun writeSupportingFiles() {
        val mappingsFileName = ClassName("$enumPackage.support", "EnumMappings")
        file(mappingsFileName) {
            toFunctions.forEach {
                addFunction(it)
            }
        }.writeTo(codeDirectory)

        file("$enumPackage.serialization", "EnumSerializers") {
            serializers.forEach { typeSpec ->
                addType(typeSpec)
            }
        }.writeTo(codeDirectory)
    }

}

private fun ClassName.buildNamePrefix(): String {
    val stringBuilder = StringBuilder()
    this.simpleNames.forEach {
        stringBuilder.append(it)
    }
    return stringBuilder.toString()
}
