package com.giffardtechnologies.restdocs

import com.allego.api.client.futureproof.EnumID
import com.allego.api.client.futureproof.ImmutableEnumID
import com.allego.meter.file
import com.allego.util.futureproofenum.FutureProof
import com.allego.util.futureproofenum.IntId
import com.allego.util.futureproofenum.LongId
import com.allego.util.futureproofenum.StringId
import com.giffardtechnologies.restdocs.codegen.convertToEnumConstantStyle
import com.giffardtechnologies.restdocs.codegen.fieldNameToClassStyle
import com.giffardtechnologies.restdocs.codegen.toConstantStyle
import com.giffardtechnologies.restdocs.domain.FieldReference
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.mappers.mapToModel
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import java.io.File
import java.util.*

class KotlinGenerator {

    data class Options(
        val codeDirectory: File,
        val dtoPackage: String,
        val verboseLogging: Boolean = false,
        val forceTopLevel: Set<FieldReference>,
        val excludedFields: Set<FieldReference>
    )

    fun generate(sourceFile: File, options: Options) {
        val document = DocValidator().getValidatedDocument(sourceFile).mapToModel()

//        val typeVariable = TypeVariableName.invoke("T")
//        val typeSpec = TypeSpec.interfaceBuilder(
//            ClassName(options.dtoPackage, "EnumID")
//        )
//            .addTypeVariable(typeVariable)
//            .addProperty("id", typeVariable)
//            .build()
//
//        file(options.dtoPackage, "EnumID") {
//            addType(
//                TypeSpec.interfaceBuilder("EnumID")
//                    .addTypeVariable(typeVariable)
//                    .addProperty("id", typeVariable)
//                    .build()
//            )
//            addType(
//                TypeSpec.classBuilder("ImmutableEnumID")
//                    .addTypeVariable(typeVariable)
//                    .addProperty("id", typeVariable)
//                    .build()
//            )
//
//        }

        for (namedEnumeration in document.enumerations) {
            processEnum(namedEnumeration, options.codeDirectory, options.dtoPackage)
        }

        document.dataObjects.filter { !it.isHidden }.forEach { dataObject ->

        }

        for (namedEnumeration in document.enumerations) {
            processEnum(namedEnumeration, options.codeDirectory, options.dtoPackage)
        }

    }

    /**
     * @param namedEnumeration   the enumeration to process
     * @param enumPackage        the package where teh enum should be placed
     * @param useFutureProofEnum whether the enum should be generating using [FutureProof] annotations
     */
    private fun processEnum(
        namedEnumeration: NamedEnumeration,
        codeDirectory: File,
        enumPackage: String,
        useFutureProofEnum: Boolean = true
    ) {
        val typeSpec: TypeSpec = processEnumToTypeSpec(namedEnumeration, enumPackage, useFutureProofEnum)
        val fileSpec = file(enumPackage, namedEnumeration.typeName) {
            addType(typeSpec)
        }

//        val enumPackageDirectory = File(codeDirectory, enumPackage.replace('.', '/'))
//        enumPackageDirectory.parentFile.mkdirs()
        fileSpec.writeTo(codeDirectory)
    }

    private fun processEnumToTypeSpec(
        namedEnumeration: NamedEnumeration,
        enumPackage: String,
        useFutureProofEnum: Boolean
    ): TypeSpec {
        val enumClassName = ClassName(enumPackage, namedEnumeration.typeName)
        return processEnumToTypeSpec(namedEnumeration, enumClassName, useFutureProofEnum)
    }

    private fun processEnumToTypeSpec(
        namedEnumeration: NamedEnumeration,
        enumClassName: ClassName,
        useFutureProofEnum: Boolean
    ): TypeSpec {
        return if (useFutureProofEnum) {
            val enumKeyTypeClass = when (namedEnumeration.type.key) {
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
            for (enumConstant in namedEnumeration.type.values) {
                var enumConstantValue = enumConstant.value
                if (namedEnumeration.type.key === DataType.StringType) {
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
            val enumKeyTypeClass = when (namedEnumeration.type.key) {
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
            for (enumConstant in namedEnumeration.type.values) {
                var enumConstantValue = enumConstant.value
                if (namedEnumeration.type.key === DataType.StringType) {
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