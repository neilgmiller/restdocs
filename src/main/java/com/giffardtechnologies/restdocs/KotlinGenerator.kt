package com.giffardtechnologies.restdocs

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.codegen.BitSetProcessor
import com.giffardtechnologies.restdocs.codegen.DataObjectProcessor
import com.giffardtechnologies.restdocs.codegen.DataObjectUsageClassifier
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
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import org.apache.commons.lang3.StringUtils
import org.apache.commons.io.FileUtils
import java.io.File

class KotlinGenerator {

    data class Options(
        val codeDirectory: File,
        val iOSCodeDirectory: File,
        val clientPackage: String,
        val verboseLogging: Boolean = false,
        val forceTopLevel: Set<FieldReference>,
        val excludedFields: Set<FieldReference>,
        val asyncJobStatusConfig: AsyncJobStatusConfig? = null,
    )

    data class AsyncJobStatusConfig(val methodID: Int)

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
        val requestsDtoPackage = requestsPackage + ".dto"

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

        val classifier = DataObjectUsageClassifier(document)
        val fieldAndTypeProcessor = FieldAndTypeProcessor(dtoPackage, dtoPackage, classifier = classifier, requestsDtoPackage = requestsDtoPackage)
        val objectProcessor =
            ObjectProcessor(options.codeDirectory, fieldAndTypeProcessor, enumProcessor, bitSetProcessor)
        val dataObjectProcessor = DataObjectProcessor(dtoPackage, requestsDtoPackage, objectProcessor, classifier)

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

        val asyncJobStatusClassNames = options.asyncJobStatusConfig?.let { config ->
            document.service?.methods?.firstOrNull { m -> m.id == config.methodID }
                ?.let { methodProcessor.getClassNames(it) }
                ?: error("asyncJobStatusConfig.methodID=${config.methodID} not found in document service methods")
        }

        if (asyncJobStatusClassNames != null) {
            val jobStatusEnumClassName = asyncJobStatusClassNames.responseClassName.nestedClass("JobStatus")
            val decodeFromString = MemberName("kotlinx.serialization", "decodeFromString")
            val typeVariable = TypeVariableName("T").copy(reified = true)
            file(ClassName("$requestsPackage.serialization", "DecodeAsyncResult")) {
                addFunction(
                    FunSpec.builder("decodeResult")
                        .addModifiers(KModifier.INLINE)
                        .addTypeVariable(typeVariable)
                        .receiver(asyncJobStatusClassNames.responseClassName)
                        .addParameter("json", ClassName("kotlinx.serialization.json", "Json"))
                        .returns(typeVariable)
                        .beginControlFlow("check(jobStatus == %T.Success)", jobStatusEnumClassName)
                        .addStatement("\"Async job did not complete successfully: \$jobStatus\"")
                        .endControlFlow()
                        .addStatement("return json.%M(requireNotNull(jobResult))", decodeFromString)
                        .build()
                )
            }.writeTo(options.codeDirectory)
        }

        file(ClassName(options.clientPackage, "SwiftAPIServerClient")) {
            addClass("SwiftAPIServerClient") {
                raw { classBuilder ->
                    val constructorBuilder = FunSpec.constructorBuilder()
                    val apiServerClientClassName = ClassName(options.clientPackage, "APIServerClient")
                    constructorBuilder.addParameter("apiServerClient", apiServerClientClassName)
                    val jsonClassName = ClassName("kotlinx.serialization.json", "Json")
                    if (asyncJobStatusClassNames != null) {
                        constructorBuilder.addParameter("json", jsonClassName)
                    }
                    classBuilder.primaryConstructor(constructorBuilder.build())
                    val propertySpecBuilder = PropertySpec.builder(
                        "apiServerClient",
                        apiServerClientClassName
                    )
                    propertySpecBuilder
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("apiServerClient")
                    classBuilder.addProperty(propertySpecBuilder.build())
                    if (asyncJobStatusClassNames != null) {
                        classBuilder.addProperty(
                            PropertySpec.builder("json", jsonClassName)
                                .addModifiers(KModifier.PRIVATE)
                                .initializer("json")
                                .build()
                        )
                    }

                    document.service?.methods?.forEach { method ->
                        val (requestClassName, responseClassName, asyncResponseClassName) = methodProcessor.getClassNames(method)
                        val methodName = StringUtils.capitalize(method.name)

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

                        if (asyncResponseClassName != null && asyncJobStatusClassNames != null) {
                            val statusRequestClassName = asyncJobStatusClassNames.requestClassName
                            val fetchMethodName = "fetch${methodName}AsyncResponse"
                            val decodeResultMember = MemberName("$requestsPackage.serialization", "decodeResult")
                            val throwsAnnotation = AnnotationSpec.builder(ClassName("kotlin", "Throws"))
                                .addMember("%T::class", ClassName(options.clientPackage, "APIException"))
                                .addMember("%T::class", ClassName("kotlinx.io", "IOException"))
                                .addMember("%T::class", ClassName("kotlin.coroutines.cancellation", "CancellationException"))
                                .build()

                            classBuilder.addFunction(
                                FunSpec.builder(fetchMethodName)
                                    .addModifiers(KModifier.SUSPEND)
                                    .addParameter("jobID", String::class.asClassName())
                                    .returns(asyncResponseClassName)
                                    .addAnnotation(throwsAnnotation)
                                    .addStatement("val status = apiServerClient.execute(%T(jobID))", statusRequestClassName)
                                    .addStatement("return status.%M(json)", decodeResultMember)
                                    .build()
                            )

                            classBuilder.addFunction(
                                FunSpec.builder("${fetchMethodName}Blocking")
                                    .addParameter("jobID", String::class.asClassName())
                                    .returns(asyncResponseClassName)
                                    .addAnnotation(throwsAnnotation)
                                    .addCode(
                                        "return %T { %L(jobID) }\n",
                                        ClassName("kotlinx.coroutines", "runBlocking"),
                                        fetchMethodName,
                                    )
                                    .build()
                            )

                            classBuilder.addFunction(
                                FunSpec.builder("${fetchMethodName}ForResult")
                                    .addParameter("jobID", String::class.asClassName())
                                    .returns(Result::class.asClassName().parameterizedBy(asyncResponseClassName))
                                    .addCode(
                                        """
                                        |return try {
                                        |    %T.success(%T { %L(jobID) })
                                        |} catch (e: Exception) {
                                        |    %T.failure(e)
                                        |}
                                        |""".trimMargin(),
                                        Result::class.asClassName(),
                                        ClassName("kotlinx.coroutines", "runBlocking"),
                                        fetchMethodName,
                                        Result::class.asClassName(),
                                    )
                                    .build()
                            )
                        }

                    }
                }
            }
        }.writeTo(options.iOSCodeDirectory)
    }

}