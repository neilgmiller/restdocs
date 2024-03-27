package com.giffardtechnologies.restdocs

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.codegen.DataObjectProcessor
import com.giffardtechnologies.restdocs.codegen.EnumProcessor
import com.giffardtechnologies.restdocs.codegen.FieldAndTypeProcessor
import com.giffardtechnologies.restdocs.codegen.MethodProcessor
import com.giffardtechnologies.restdocs.codegen.ObjectProcessor
import com.giffardtechnologies.restdocs.domain.FieldReference
import com.giffardtechnologies.restdocs.mappers.mapToModel
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

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
        val enumProcessor = EnumProcessor(options.codeDirectory, dtoPackage)

        for (namedEnumeration in document.enumerations) {
            enumProcessor.processEnum(namedEnumeration)
        }

        val fieldAndTypeProcessor = FieldAndTypeProcessor(dtoPackage, dtoPackage)
        val objectProcessor = ObjectProcessor(options.codeDirectory, fieldAndTypeProcessor, enumProcessor)
        val dataObjectProcessor = DataObjectProcessor(dtoPackage, objectProcessor)

        document.dataObjects.filter { !it.isHidden }.forEach { dataObject ->
            dataObjectProcessor.generateDataObjectClassFile(dataObject)
        }

        val methodProcessor = MethodProcessor(
            options.codeDirectory,
            options.clientPackage + ".requests",
            dtoPackage,
            fieldAndTypeProcessor,
            enumProcessor,
            supportPackage = options.clientPackage + ".support.request"
        )

        document.service?.methods?.forEach {
            methodProcessor.processMethod(it)
        }

        enumProcessor.writeSupportingFiles()

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
                        val (requestClassName, responseClassName) = methodProcessor.getClassNames(it)
                        classBuilder.addFunction(
                            FunSpec.builder("executeBlocking")
                                .addParameter("request", requestClassName)
                                .returns(responseClassName)
                                .addAnnotation(
                                    AnnotationSpec.builder(ClassName("kotlin", "Throws"))
                                        .addMember("%T::class", ClassName(options.clientPackage, "APIException"))
                                        .addMember("%T::class", ClassName("io.ktor.utils.io.errors", "IOException"))
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
                    }
                }
            }
        }.writeTo(options.iOSCodeDirectory)
    }

}