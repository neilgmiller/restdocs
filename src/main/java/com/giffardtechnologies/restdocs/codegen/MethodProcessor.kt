package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.apache.commons.lang3.StringUtils
import java.io.File
import com.giffardtechnologies.restdocs.domain.type.TypeSpec as DomainTypeSpec

/**
 * Processes a [Method] definition to generate the corresponding request and response classes.
 * This class is responsible for creating the necessary files for API calls, including serialization
 * and deserialization logic.
 *
 * @property codeDirectory The directory where the generated code will be written.
 * @property requestsPackage The package name for the generated request classes.
 * @property typeRefPackage The package name for referenced types.
 * @property fieldAndTypeProcessor A processor for handling fields and types within the method.
 * @property enumProcessor A processor for handling enums.
 * @property bitSetProcessor A processor for handling bitsets.
 * @property usePath A flag to determine if path-based requests should be used.
 * @property supportPackage The package name for supporting classes.
 * @property asyncControlParameterName The service-level default name of the boolean parameter
 * that switches a method between sync and async response shapes (mirrors
 * [com.giffardtechnologies.restdocs.domain.Service.Common.asyncControlParameterName]); overridden
 * per-method by [Method.asyncControlParameter].
 */
class MethodProcessor(
    private val codeDirectory: File,
    private val requestsPackage: String,
    private val typeRefPackage: String,
    private val fieldAndTypeProcessor: FieldAndTypeProcessor,
    enumProcessor: EnumProcessor,
    bitSetProcessor: BitSetProcessor,
    private val usePath: Boolean = false,
    supportPackage: String = "$requestsPackage.support",
    private val asyncControlParameterName: String? = null,
) {

    /**
     * Returns the [Field] among [method]'s parameters that controls its sync/async response
     * shape, resolving [Method.asyncControlParameter] against [asyncControlParameterName], or
     * `null` if no such parameter is resolved or configured.
     */
    private fun resolveAsyncControlField(method: Method): Field? {
        val name = method.asyncControlParameter ?: asyncControlParameterName ?: return null
        return method.parameters.find { it.name == name }.getOrNull()
    }

    private val mAuthenticatedAllegoRequestClassName = ClassName(
        supportPackage,
        "AllegoAuthenticatedRequest"
    )

    private val mAllegoRequestClassName = ClassName(
        supportPackage,
        "AllegoOpenRequest"
    )

    private val mAllegoBaseRequestClassName = ClassName(
        supportPackage,
        "AllegoRequest"
    )

    private val mAllegoPathAndBodyRequestClassName: ClassName = ClassName(
        supportPackage,
        "AllegoBodyStackRequest"
    )

    private val deserializeWhenBlock = CodeBlock.builder()
    private val serializeWhenBlock = CodeBlock.builder()

    private val encodeToString = MemberName("kotlinx.serialization", "encodeToString")
    private val decodeFromJsonElement = MemberName("kotlinx.serialization.json", "decodeFromJsonElement")


    /**
     * Writes the supporting files for serialization and deserialization.
     * This includes a file for deserializing parameters from a JSON string and another for
     * serializing a base request to a JSON string.
     */
    fun writeSupportingFiles() {
        val mappingsFileName = ClassName(
            "$requestsPackage.serialization",
            "DeserializeFromParams"
        )
        file(mappingsFileName) {
            addFunction(
                FunSpec.builder("deserializeFromParams")
                    .addParameter("json", Json::class.asClassName())
                    .addParameter("methodID", Int::class.asClassName())
                    .addParameter("jsonString", String::class.asClassName())
                    .returns(mAllegoBaseRequestClassName.parameterizedBy(STAR))
                    .addCode(
                        CodeBlock.builder()
                            .beginControlFlow("return when(methodID) {")
                            .add(deserializeWhenBlock.build())
                            .add("else -> throw IllegalArgumentException(\"Unknown method ID: \$methodID\")")
                            .endControlFlow()
                            .build()
                    )
                    .build()
            )
        }.writeTo(codeDirectory)

        val serializerFileName = ClassName(
            "$requestsPackage.serialization",
            "SerializeParamsFromBaseRequest"
        )
        file(serializerFileName) {
            addFunction(
                FunSpec.builder("serializeParamsFromBaseRequest")
                    .addParameter("json", Json::class.asClassName())
                    .addParameter("request", mAllegoBaseRequestClassName.parameterizedBy(STAR))
                    .returns(String::class.asClassName())
                    .addCode(
                        CodeBlock.builder()
                            .beginControlFlow("return when(request) {")
                            .add(serializeWhenBlock.build())
                            .add("else -> throw IllegalArgumentException(\"Unknown request call: \$request::class.name\")")
                            .endControlFlow()
                            .build()
                    )
                    .build()
            )
        }.writeTo(codeDirectory)
    }

    /**
     * Returns a [FunSpec] for a function that deserializes parameters from a JSON string based on a method ID.
     *
     * @return A [FunSpec] for the `deserializeFromParams` function.
     */
    fun getDeserializeFromParamsFunSpec(): FunSpec {
        return FunSpec.builder("deserializeFromParams")
            .addParameter("json", Json::class.asClassName())
            .addParameter("methodID", Long::class.asClassName())
            .addParameter("jsonString", String::class.asClassName())
            .returns(mAllegoBaseRequestClassName)
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow("return when(methodID) {")
                    .add(deserializeWhenBlock.build())
                    .add("else -> TODO()")
                    .endControlFlow()
                    .build()
            )
            .build()
//        return deserializeFromParamsFunSpecBuilder.build()
    }

    private val objectProcessor: ObjectProcessor =
        ObjectProcessor(codeDirectory, fieldAndTypeProcessor, enumProcessor, bitSetProcessor)

    private data class ResponseClassDefinition(val className: ClassName, val typeSpec: TypeSpec? = null)

    /**
     * Holds the generated class names for a method's request, response, and async response.
     *
     * @property requestClassName The [ClassName] for the generated request class. When
     *   [hasAsyncControlParameter] is true, this is a container class (not itself constructible)
     *   holding the nested `Async`/`Sync` request classes; otherwise it's the request class itself.
     * @property responseClassName The [ClassName] for the generated response class.
     * @property asyncResponseClassName The [ClassName] for the generated async response class,
     *   or null when the method has no `asyncResponse` block.
     * @property hasAsyncControlParameter Whether the method has a resolved async control
     *   parameter, in which case [requestClassName] generates nested `Async`/`Sync` classes
     *   instead of being directly constructible.
     */
    data class MethodClassNames(
        val requestClassName: ClassName,
        val responseClassName: ClassName,
        val asyncResponseClassName: ClassName? = null,
        val hasAsyncControlParameter: Boolean = false,
    ) {
        /**
         * The [ClassName] to construct/pass to `execute()` for the async (job-returning) path.
         * For methods without a resolved async control parameter, this is just [requestClassName]
         * itself.
         */
        val asyncRequestClassName: ClassName
            get() = if (hasAsyncControlParameter) requestClassName.nestedClass("Async") else requestClassName

        /**
         * The [ClassName] to construct/pass to `execute()` for the sync (payload-returning) path,
         * or `null` when the method has no resolved async control parameter.
         */
        val syncRequestClassName: ClassName?
            get() = if (hasAsyncControlParameter) requestClassName.nestedClass("Sync") else null
    }

    /**
     * Generates the class names for a given [Method].
     *
     * @param method The method to generate class names for.
     * @return A [MethodClassNames] instance containing the request and response class names.
     */
    fun getClassNames(method: Method): MethodClassNames {
        val methodName = StringUtils.capitalize(method.name)
        val requestClassName = ClassName(requestsPackage, methodName + "Request")

        val isAsyncMethod = method.jobResponse != null
        val effectiveResponse = if (isAsyncMethod) method.jobResponse else method.response
        val effectiveAsyncResponse = if (isAsyncMethod) method.payloadResponse else null

        val responseClassName = effectiveResponse?.let { response ->
            when (response.typeSpec) {
                is DomainTypeSpec.TypeRefSpec -> {
                    ClassName(typeRefPackage, response.typeSpec.referenceName)
                }

                is DomainTypeSpec.ObjectSpec -> {
                    ClassName(requestsPackage, methodName + "Response")
                }

                else -> null
            }
        } ?: Unit::class.asClassName()

        val asyncResponseClassName = effectiveAsyncResponse?.let { asyncResponse ->
            when (val spec = asyncResponse.typeSpec) {
                is DomainTypeSpec.ObjectSpec -> {
                    ClassName(requestsPackage, methodName + "AsyncResponse")
                }

                null -> Unit::class.asClassName()

                else -> fieldAndTypeProcessor.getScalarTypeName(spec)
            }
        }

        val hasAsyncControlParameter = resolveAsyncControlField(method) != null

        return MethodClassNames(requestClassName, responseClassName, asyncResponseClassName, hasAsyncControlParameter)
    }

    /**
     * Processes a single [Method] to generate its corresponding request and response classes.
     *
     * @param method The method to process.
     */
    fun processMethod(method: Method) {
        val (requestClassName, responseClassName, asyncResponseClassName, hasAsyncControlParameter) = getClassNames(method)
        // Path-only methods (no id) are not yet supported — see PROJECT.md Out of Scope
        val methodId = requireNotNull(method.id) {
            "Method '${method.name}' has no id — path-based dispatch is not yet supported in MethodProcessor"
        }

        val asyncControlField = resolveAsyncControlField(method)
        val superClassName = getSuperClassName(method)

        val requestClassBuilder = TypeSpec.classBuilder(requestClassName)
            .addModifiers(KModifier.PUBLIC)

        // When the method has a resolved async control parameter, `requestClassName` becomes a
        // container (not itself constructible or a request) holding nested `Async`/`Sync` request
        // classes instead of being the request class itself — see the branch below.
        if (!hasAsyncControlParameter) {
            requestClassBuilder
                .superclass(superClassName.parameterizedBy(responseClassName))
                .addSuperclassConstructorParameter("%L", methodId)

            if (method.deprecated) {
                requestClassBuilder.addAnnotation(
                    AnnotationSpec.builder(Deprecated::class)
                        .addMember("message = %S", method.deprecationNote ?: "")
                        .build()
                )
            }
        }

        if (method.parameters.isEmpty) {
            requestClassBuilder.addSuperclassConstructorParameter("%N", "Unit")
            deserializeWhenBlock.addStatement(
                "%L -> %T()",
                methodId,
                requestClassName,
            )
            serializeWhenBlock.addStatement(
                "is %T -> \"\"",
                requestClassName,
            )
            Unit::class.asClassName()
        } else {
            val paramsClassName = requestClassName.nestedClass("Params")

            val paramsTypeSpec = objectProcessor.processObjectToTypeSpec(
                paramsClassName,
                method.parameters,
                useFutureProofEnum = false,
                completeConstructor = true,
                initializeWithDefault = false,
                initializeCollections = false,
                subObjectClassNameFactory = { parentClassName, field ->
                    if (parentClassName == paramsClassName) {
                        requestClassName.nestedClass(fieldToClassStyle(field, parentClassName.simpleName, asInner = true))
                    } else {
                        parentClassName.nestedClass(fieldToClassStyle(field, parentClassName.simpleName, asInner = true))
                    }
                },
                subObjectTypeSpecHandler = { classBuilder, subObjectClassName, subObjectTypeSpec ->
                    if (subObjectClassName.enclosingClassName() == requestClassName) {
                        requestClassBuilder.addType(subObjectTypeSpec)
                    } else {
                        classBuilder.addType(subObjectTypeSpec)
                    }
                }
            )
            requestClassBuilder.addType(paramsTypeSpec)

            // make the constructor that the builder will use; when the method has an async control
            // field, it's forced to `true` or `false` here and omitted from the public constructor
            // entirely — callers never see or set it (see the nested `Async`/`Sync` classes below).
            fun buildParamsForwardingConstructor(forceControlFieldTrue: Boolean): FunSpec {
                val constructorBuilder = FunSpec.constructorBuilder()
                val formatBuilder = StringBuilder("Params(")
                val parameterNames = ArrayList<String>()

                method.parameters.forEach { field ->
                    if (asyncControlField != null && field.name == asyncControlField.name) {
                        formatBuilder.append(if (forceControlFieldTrue) "true," else "false,")
                        return@forEach
                    }
                    // largely copied code from ObjectProcessor
                    val propertySpec = fieldAndTypeProcessor.createPropertySpec(
                        field,
                        false,
                        objectClassName = requestClassName,
                        initializeWithDefault = false,
                        initializeCollections = false,
                    )
                    val parameterSpecBuilder = ParameterSpec.builder(field.longName, propertySpec.type)
                    if (propertySpec.type.isNullable) {
                        constructorBuilder.addParameter(
                            parameterSpecBuilder.defaultValue("null").build()
                        )
                    } else {
                        parameterSpecBuilder.defaultValue(propertySpec.initializer)
                        constructorBuilder.addParameter(parameterSpecBuilder.build())
                    }
                    formatBuilder.append("%N,")
                    parameterNames.add(field.longName)
                }
                formatBuilder.append(")")

                return constructorBuilder
                    .callThisConstructor(CodeBlock.of(formatBuilder.toString(), *parameterNames.toTypedArray()))
                    .build()
            }

            if (hasAsyncControlParameter && asyncResponseClassName != null) {
                // The container itself is never constructed — it just holds the nested Async/Sync
                // request classes plus their shared Params.
                requestClassBuilder.primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )

                fun buildVariantClassBuilder(nestedName: String, variantResponseClassName: ClassName): TypeSpec.Builder {
                    val variantClassName = requestClassName.nestedClass(nestedName)
                    val variantBuilder = TypeSpec.classBuilder(variantClassName)
                        .superclass(superClassName.parameterizedBy(variantResponseClassName))
                        .addSuperclassConstructorParameter("%L", methodId)
                        .addSuperclassConstructorParameter("params")
                        .addModifiers(KModifier.PUBLIC)

                    if (method.deprecated) {
                        variantBuilder.addAnnotation(
                            AnnotationSpec.builder(Deprecated::class)
                                .addMember("message = %S", method.deprecationNote ?: "")
                                .build()
                        )
                    }

                    // internal (not private) so the container's companion object — a sibling
                    // nested class, not this class itself — can still construct it from raw,
                    // undecoded params; it stays out of the class's public API either way.
                    variantBuilder.primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addModifiers(KModifier.INTERNAL)
                            .addParameter("params", paramsClassName)
                            .build()
                    )
                    variantBuilder.addFunction(
                        FunSpec.builder("deserializeResponse")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("json", Json::class.asClassName())
                            .addParameter("jsonElement", JsonElement::class.asClassName())
                            .returns(variantResponseClassName)
                            .addCode("return json.%M(jsonElement)", decodeFromJsonElement)
                            .build()
                    )
                    return variantBuilder
                }

                val asyncClassName = requestClassName.nestedClass("Async")
                val asyncBuilder = buildVariantClassBuilder("Async", responseClassName)
                asyncBuilder.addFunction(buildParamsForwardingConstructor(forceControlFieldTrue = true))
                requestClassBuilder.addType(asyncBuilder.build())

                val syncClassName = requestClassName.nestedClass("Sync")
                val syncBuilder = buildVariantClassBuilder("Sync", asyncResponseClassName)
                syncBuilder.addFunction(buildParamsForwardingConstructor(forceControlFieldTrue = false))
                requestClassBuilder.addType(syncBuilder.build())

                requestClassBuilder.addType(
                    TypeSpec.companionObjectBuilder()
                        .addFunction(
                            FunSpec.builder("deserializeFromParams")
                                .addParameter("json", Json::class.asClassName())
                                .addParameter("jsonString", String::class.asClassName())
                                .returns(asyncClassName)
                                .addCode(
                                    CodeBlock.of("return %T(json.decodeFromString<%T>(jsonString))", asyncClassName, paramsClassName)
                                )
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("serializeFromParams")
                                .addParameter("json", Json::class.asClassName())
                                .addParameter("request", mAllegoBaseRequestClassName.parameterizedBy(STAR))
                                .returns(String::class.asClassName())
                                .addCode(
                                    CodeBlock.of("return json.%1M<%2T>(request.requestParams as %2T)", encodeToString, paramsClassName)
                                )
                                .build()
                        )
                        .build()
                )

                deserializeWhenBlock.addStatement(
                    "%L -> %T.deserializeFromParams(json, jsonString)",
                    methodId,
                    requestClassName,
                )
                serializeWhenBlock.addStatement(
                    "is %1T -> %2T.serializeFromParams(json, request)",
                    asyncClassName,
                    requestClassName,
                )
                serializeWhenBlock.addStatement(
                    "is %1T -> %2T.serializeFromParams(json, request)",
                    syncClassName,
                    requestClassName,
                )
            } else {
                requestClassBuilder.addSuperclassConstructorParameter("params")

                requestClassBuilder.primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("params", paramsClassName)
                        .build()
                )

                requestClassBuilder.addFunction(buildParamsForwardingConstructor(forceControlFieldTrue = true))

                requestClassBuilder.addType(
                    TypeSpec.companionObjectBuilder()
                        .addFunction(
                            FunSpec.builder("deserializeFromParams")
                                .addParameter("json", Json::class.asClassName())
                                .addParameter("jsonString", String::class.asClassName())
                                .returns(requestClassName)
                                .addCode(
                                    CodeBlock.of("return %T(json.decodeFromString<%T>(jsonString))", requestClassName, paramsClassName)
                                )
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("serializeFromParams")
                                .addParameter("json", Json::class.asClassName())
                                .addParameter("request", requestClassName)
                                .returns(String::class.asClassName())
                                .addCode(
                                    CodeBlock.of("return json.%1M<%2T>(request.requestParams as %2T)", encodeToString, paramsClassName)
                                )
                                .build()
                        )
                        .build()
                )

                deserializeWhenBlock.addStatement(
                    "%L -> %T.deserializeFromParams(json, jsonString)",
                    methodId,
                    requestClassName,
                )
                serializeWhenBlock.addStatement(
                    "is %1T -> %1T.serializeFromParams(json, request)",
                    requestClassName,
                )
            }
        }

        if (!hasAsyncControlParameter) {
            requestClassBuilder.addFunction(
                FunSpec.builder("deserializeResponse")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("json", Json::class.asClassName())
                    .addParameter("jsonElement", JsonElement::class.asClassName())
                    .returns(responseClassName)
                    .addCode("return json.%M(jsonElement)", decodeFromJsonElement)
                    .build()
            )
        }

        TypeSpec.interfaceBuilder(ClassName(requestClassName.packageName, requestClassName.simpleName, "RequestConfig"))

        val groupedParameters = method.parameters.groupBy { it.isRequired }
        val requiredParameters = groupedParameters[true]
        val optionalParameters = groupedParameters[false]

        val builder = FunSpec.builder("build")

        TypeSpec.companionObjectBuilder()
            .addFunction(
                builder.build()
            )

        val isAsyncMethod = method.jobResponse != null
        val effectiveResponse = if (isAsyncMethod) method.jobResponse else method.response
        val effectiveAsyncResponse = if (isAsyncMethod) method.payloadResponse else null

        val responseClassTypeSpec = createResponseClassDefinition(effectiveResponse, responseClassName)
        val asyncResponseClassTypeSpec = asyncResponseClassName?.let {
            createAsyncResponseClassDefinition(effectiveAsyncResponse, it)
        }

        file(requestClassName) {
            addType(requestClassBuilder.build())
            responseClassTypeSpec?.let {
                addType(responseClassTypeSpec)
            }
            asyncResponseClassTypeSpec?.let {
                addType(asyncResponseClassTypeSpec)
            }
        }
            .writeTo(codeDirectory)
    }

    private fun getSuperClassName(method: Method): ClassName {
        val baseClassName = if (method.isAuthenticationRequired) {
            mAuthenticatedAllegoRequestClassName
        } else {
            if (usePath && method.id == null) {
                mAllegoPathAndBodyRequestClassName
            } else {
                mAllegoRequestClassName
            }
        }
        return baseClassName
    }

    private fun createResponseClassDefinition(
        response: Response?,
        responseClassName: ClassName
    ): TypeSpec? {
        return response?.let {
            when (response.typeSpec) {
                is DomainTypeSpec.ObjectSpec -> {
                        objectProcessor.processObjectToTypeSpec(
                            responseClassName,
                            response.typeSpec,
                            useFutureProofEnum = true,
                            forceTopLevel = false,
                    )
                }

                else -> null
            }
        }
    }

    private fun createAsyncResponseClassDefinition(
        asyncResponse: Response?,
        asyncResponseClassName: ClassName
    ): TypeSpec? {
        return asyncResponse?.let {
            when (asyncResponse.typeSpec) {
                is DomainTypeSpec.ObjectSpec -> {
                    objectProcessor.processObjectToTypeSpec(
                        asyncResponseClassName,
                        asyncResponse.typeSpec,
                        useFutureProofEnum = true,
                        forceTopLevel = false,
                    )
                }

                else -> null
            }
        }
    }

    private fun addMinimalConstructor(
        method: Method,
        requestClassName: ClassName,
        requestClassBuilder: TypeSpec.Builder
    ) {
        val builder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
        val description = StringUtils.uncapitalize(method.description)

//			if (method.getParameters().isEmpty()) {
        if (method.isAuthenticationRequired) {
            builder.addParameter("accessKey", String::class)
                .addStatement("super(\$N, null)", "accessKey")
                .addKdoc(
                    CodeBlock.builder()
                        .add("Creates a request that $description")
                        .add(" @param accessKey the access key to use for the request")
                        .build()
                )
        } else {
            builder.addStatement("super(null)")
                .addKdoc(CodeBlock.builder().add("Creates a request that $description").build())
        }
        //			} else {
//				if (method.isAuthenticationRequired()) {
//					builder.addParameter(String.class, "accessKey")
//					       .addStatement("super($N, null)", "accessKey")
//					       .addKdoc(CodeBlock.builder()
//					                            .add("Creates a request that " + description)
//					                            .add(" @param accessKey the access key to use for the request")
//					                            .build());
//				} else {
//					builder.addStatement("super(null)")
//					       .addKdoc(CodeBlock.builder().add("Creates a request that " + description).build());
//
//				}
//				processParamsForConstructor(method, requestClassName, builder);
//			}
        requestClassBuilder.addFunction(builder.build())
    }
}
