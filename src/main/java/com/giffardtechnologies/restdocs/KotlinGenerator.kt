package com.giffardtechnologies.restdocs

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.codegen.BitSetProcessor
import com.giffardtechnologies.restdocs.codegen.DataObjectProcessor
import com.giffardtechnologies.restdocs.codegen.DataObjectUsageClassifier
import com.giffardtechnologies.restdocs.codegen.EnumProcessor
import com.giffardtechnologies.restdocs.codegen.FieldAndTypeProcessor
import com.giffardtechnologies.restdocs.codegen.MethodProcessor
import com.giffardtechnologies.restdocs.codegen.ObjectProcessor
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.FieldReference
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Service
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import kotlinx.datetime.LocalDate
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
        val skipDeprecatedBefore: LocalDate? = null,
    )

    data class AsyncJobStatusConfig(val methodID: Int)

    fun generate(document: Document, options: Options) {
        val document = document.filterDeprecatedMethods(options.skipDeprecatedBefore, options.verboseLogging)

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
            supportPackage = options.clientPackage + ".support.request",
            asyncControlParameterName = document.service?.common?.asyncControlParameterName,
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
                        val classNames = methodProcessor.getClassNames(method)
                        val (requestClassName, responseClassName, asyncResponseClassName) = classNames
                        val methodName = StringUtils.capitalize(method.name)
                        val throwsAnnotationBuilder = {
                            AnnotationSpec.builder(ClassName("kotlin", "Throws"))
                                .addMember("%T::class", ClassName(options.clientPackage, "APIException"))
                                .addMember("%T::class", ClassName("kotlinx.io", "IOException"))
                                .addMember("%T::class", ClassName("kotlin.coroutines.cancellation", "CancellationException"))
                                .build()
                        }

                        // executeName/requestType/responseType vary depending on whether this method has a
                        // resolved async control parameter: normally a single `execute*` trio is generated;
                        // when the control parameter is present, callers instead get `executeAsync*` (returns
                        // the job container, via the nested `Async` class which forces the parameter to
                        // `true` internally) and `executeSync*` (returns the payload directly, via the
                        // nested `Sync` class which forces it to `false`) — the parameter itself is never
                        // exposed to callers.
                        data class ExecuteVariant(val namePrefix: String, val requestType: ClassName, val responseType: ClassName)
                        val syncRequestClassName = classNames.syncRequestClassName
                        val executeVariants = if (syncRequestClassName != null && asyncResponseClassName != null) {
                            listOf(
                                ExecuteVariant("executeAsync", classNames.asyncRequestClassName, responseClassName),
                                ExecuteVariant("executeSync", syncRequestClassName, asyncResponseClassName),
                            )
                        } else {
                            listOf(ExecuteVariant("execute", classNames.asyncRequestClassName, responseClassName))
                        }

                        executeVariants.forEach { variant ->
                            classBuilder.addFunction(
                                FunSpec.builder(variant.namePrefix)
                                    .addParameter("request", variant.requestType)
                                    .addModifiers(KModifier.SUSPEND)
                                    .returns(variant.responseType)
                                    .addAnnotation(throwsAnnotationBuilder())
                                    .addCode("return apiServerClient.execute(request)")
                                    .build()
                            )

                            classBuilder.addFunction(
                                FunSpec.builder("${variant.namePrefix}Blocking")
                                    .addParameter("request", variant.requestType)
                                    .returns(variant.responseType)
                                    .addAnnotation(throwsAnnotationBuilder())
                                    .addCode("""
                                        | return %T {
                                        |   apiServerClient.execute(request)
                                        | }
                                    """.trimMargin("|"), ClassName("kotlinx.coroutines", "runBlocking")
                                    )
                                    .build()
                            )

                            classBuilder.addFunction(
                                FunSpec.builder("${variant.namePrefix}ForResult")
                                    .addParameter("request", variant.requestType)
                                    .returns(Result::class.asClassName().parameterizedBy(variant.responseType))
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

    private fun Document.filterDeprecatedMethods(skipDeprecatedBefore: LocalDate?, verboseLogging: Boolean): Document {
        val service = this.service ?: return this
        if (skipDeprecatedBefore == null) return this
        if (verboseLogging) {
            service.methods.filter { it.shouldSkipForCodegen(skipDeprecatedBefore) }.forEach { method ->
                println("Skipping deprecated method '${method.name}' (deprecatedSince=${method.deprecatedSince ?: "unknown"})")
            }
        }
        val kept = service.methods.filter { !it.shouldSkipForCodegen(skipDeprecatedBefore) }
        return Document(
            title, bitsets, enumerations, dataObjects,
            Service(service.description, service.basePath, service.common, kept)
        )
    }

    private fun Method.shouldSkipForCodegen(skipDeprecatedBefore: LocalDate): Boolean {
        if (!deprecated) return false
        val since = deprecatedSince ?: return true
        return since < skipDeprecatedBefore
    }

}