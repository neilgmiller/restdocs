package com.giffardtechnologies.restdocs.codegen

import com.allego.api.client.futureproof.EnumID
import com.allego.api.client.futureproof.ImmutableEnumID
import com.allego.meter.file
import com.allego.util.futureproofenum.FutureProof
import com.allego.util.futureproofenum.IntId
import com.allego.util.futureproofenum.LongId
import com.allego.util.futureproofenum.StringId
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.EnumSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import java.io.File

/**
 *
 * @param enumPackage        the package where the enum should be placed
 */
class EnumProcessor(
    private val codeDirectory: File,
    private val enumPackage: String,
) {

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

//        val enumPackageDirectory = File(codeDirectory, enumPackage.replace('.', '/'))
//        enumPackageDirectory.parentFile.mkdirs()
        fileSpec.writeTo(codeDirectory)
    }

    private fun processEnumToTypeSpec(
        namedEnumeration: NamedEnumeration,
        useFutureProofEnum: Boolean
    ): TypeSpec {
        val enumClassName = ClassName(enumPackage, namedEnumeration.typeName)
        return processEnumToTypeSpec(enumClassName, namedEnumeration.type, useFutureProofEnum)
    }

    fun processEnumToTypeSpec(
        enumClassName: ClassName,
        enumSpec: EnumSpec<*>,
        useFutureProofEnum: Boolean
    ): TypeSpec {
        return if (useFutureProofEnum) {
            val enumKeyTypeClass = when (enumSpec.key) {
                DataType.IntType -> {
                    Int::class
                }

                DataType.LongType -> {
                    Long::class
                }

                DataType.StringType -> {
                    String::class
                }
            }
            val builder = TypeSpec.classBuilder(enumClassName).addModifiers(KModifier.SEALED)
                .primaryConstructor(FunSpec.constructorBuilder().addParameter("id", enumKeyTypeClass).build())
                .addSuperinterface(
                    superinterface = EnumID::class.asClassName().parameterizedBy(enumKeyTypeClass.asClassName()),
                    delegate = CodeBlock.of("%T(id)", ImmutableEnumID::class.asClassName())
                )
            builder.addType(
                TypeSpec.classBuilder(enumClassName.nestedClass("Unknown"))
                    .primaryConstructor(FunSpec.constructorBuilder().addParameter("id", enumKeyTypeClass).build())
                    .superclass(enumClassName)
                    .addSuperclassConstructorParameter(CodeBlock.of("%N", "id")).build()
            )
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
            }
            builder.build()
        } else {
            TODO("what approach")
            val builder = TypeSpec.enumBuilder(enumClassName).addModifiers(KModifier.PUBLIC)
            val enumKeyTypeClass = when (enumSpec.key) {
                DataType.IntType -> {
                    builder.addSuperinterface(IntId::class.asClassName())
                    Int::class
                }

                DataType.LongType -> {
                    builder.addSuperinterface(LongId::class.asClassName())
                    Long::class
                }

                DataType.StringType -> {
                    builder.addSuperinterface(StringId::class.asClassName())
                    String::class
                }
            }
            for (enumConstant in enumSpec.values) {
                var enumConstantValue = enumConstant.value
                if (enumSpec.key === DataType.StringType) {
                    enumConstantValue = "\"" + enumConstantValue + "\""
                }
                builder.addEnumConstant(
                    convertToEnumConstantStyle(enumConstant.longName)
                )
            }
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
            return builder.build()
        }
    }

}