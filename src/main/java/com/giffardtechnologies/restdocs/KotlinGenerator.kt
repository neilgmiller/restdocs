package com.giffardtechnologies.restdocs

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.codegen.BitSetProcessor
import com.giffardtechnologies.restdocs.codegen.DataObjectProcessor
import com.giffardtechnologies.restdocs.codegen.EnumProcessor
import com.giffardtechnologies.restdocs.codegen.FieldAndTypeProcessor
import com.giffardtechnologies.restdocs.codegen.MethodProcessor
import com.giffardtechnologies.restdocs.codegen.ObjectProcessor
import com.giffardtechnologies.restdocs.domain.FieldReference
import com.giffardtechnologies.restdocs.mappers.mapToModel
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.apache.commons.io.FileUtils
import java.io.File

class KotlinGenerator {

    data class Options(
        val codeDirectory: File,
        val iOSCodeDirectory: File,
        val clientPackage: String,
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

        val dtoPackage = options.clientPackage + ".dto"
        val requestsPackage = options.clientPackage + ".requests"

        // clean out old generated code
        val dtoDir = File(options.codeDirectory.absolutePath + "/" + dtoPackage.replace('.', '/'))
        FileUtils.deleteDirectory(dtoDir)
        val requestDir = File(options.codeDirectory.absolutePath + "/" + requestsPackage.replace('.', '/'))
        FileUtils.deleteDirectory(requestDir)

        // start generation
        val enumProcessor = EnumProcessor(options.codeDirectory, dtoPackage)

        for (namedEnumeration in document.enumerations) {
            enumProcessor.processEnum(namedEnumeration)
        }

        val bitSetProcessor = BitSetProcessor(options.codeDirectory, dtoPackage)

        for (namedBitSet in document.bitsets) {
            bitSetProcessor.processBitSet(namedBitSet)
        }

        val fieldAndTypeProcessor = FieldAndTypeProcessor(dtoPackage, dtoPackage)
        val objectProcessor =
            ObjectProcessor(options.codeDirectory, fieldAndTypeProcessor, enumProcessor, bitSetProcessor)
        val dataObjectProcessor = DataObjectProcessor(dtoPackage, objectProcessor)

        document.dataObjects.filter { !it.isHidden }.forEach { dataObject ->
            dataObjectProcessor.generateDataObjectClassFile(dataObject)
        }

        val methodProcessor = MethodProcessor(
            options.codeDirectory,
            requestsPackage,
            dtoPackage,
            fieldAndTypeProcessor,
            enumProcessor,
            bitSetProcessor,
            supportPackage = options.clientPackage + ".support.request"
        )

        document.service?.methods?.forEach {
            methodProcessor.processMethod(it)
        }

        bitSetProcessor.writeSupportingFiles()
        enumProcessor.writeSupportingFiles()
        methodProcessor.writeSupportingFiles()

        file(ClassName(options.clientPackage, "SwiftAPIServerClient")) {
            addClass("SwiftAPIServerClient") {
                raw { classBuilder ->
                    val constructorBuilder = FunSpec.constructorBuilder()
                    val apiServerClientClassName = ClassName(options.clientPackage, "APIServerClient")
                    constructorBuilder.addParameter("apiServerClient", apiServerClientClassName)
                    classBuilder.primaryConstructor(constructorBuilder.build())
                    val propertySpecBuilder = PropertySpec.builder(
                        "apiServerClient",
                        apiServerClientClassName
                    )
                    propertySpecBuilder
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("apiServerClient")
                    classBuilder.addProperty(propertySpecBuilder.build())

                    document.service?.methods?.forEach {
                        val (requestClassName, responseClassName, _) = methodProcessor.getClassNames(it)

                        classBuilder.addFunction(
                            FunSpec.builder("execute")
                                .addParameter("request", requestClassName)
                                .addModifiers(KModifier.SUSPEND)
                                .returns(responseClassName)
                                .addAnnotation(
                                    AnnotationSpec.builder(ClassName("kotlin", "Throws"))
                                        .addMember("%T::class", ClassName(options.clientPackage, "APIException"))
                                        .addMember("%T::class", ClassName("kotlinx.io", "IOException"))
                                        .addMember("%T::class", ClassName("kotlin.coroutines.cancellation", "CancellationException"))
                                        .build()
                                )
                                .addCode("return apiServerClient.execute(request)")
                                .build()
                        )

                        classBuilder.addFunction(
                            FunSpec.builder("executeBlocking")
                                .addParameter("request", requestClassName)
                                .returns(responseClassName)
                                .addAnnotation(
                                    AnnotationSpec.builder(ClassName("kotlin", "Throws"))
                                        .addMember("%T::class", ClassName(options.clientPackage, "APIException"))
                                        .addMember("%T::class", ClassName("kotlinx.io", "IOException"))
                                        .addMember("%T::class", ClassName("kotlin.coroutines.cancellation", "CancellationException"))
                                        .build()
                                )
                                .addCode("""
                                    | return %T {
                                    |   apiServerClient.execute(request)
                                    | }
                                """.trimMargin("|"), ClassName("kotlinx.coroutines", "runBlocking")
                                )
                                .build()
                        )

                        classBuilder.addFunction(
                            FunSpec.builder("executeForResult")
                                .addParameter("request", requestClassName)
                                .returns(Result::class.asClassName().parameterizedBy(responseClassName))
                                .addCode("""
                                    | return try {
                                    |     val response = %T {
                                    |         apiServerClient.execute(request)
                                    |     }
                                    |     Result.success(response)
                                    | } catch (e: Exception) {
                                    |     Result.failure(e)
                                    | }
                                """.trimMargin("|"), ClassName("kotlinx.coroutines", "runBlocking")
                                )
                                .build()
                        )

                    }
                }
            }
        }.writeTo(options.iOSCodeDirectory)
    }

}