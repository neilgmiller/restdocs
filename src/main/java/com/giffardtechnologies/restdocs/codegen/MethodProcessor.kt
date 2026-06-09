package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
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
) {

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

//    private val deserializeFromParamsFunSpecBuilder = FunSpec.builder("deserializeFromParams")
//        .addParameter("json", Json::class.asClassName())
//        .addParameter("methodID", Long::class.asClassName())
//        .addParameter("jsonString", String::class.asClassName())
//        .returns(mAllegoBaseRequestClassName)
//        .addCode(
//            CodeBlock.builder()
//                .beginControlFlow("when(methodID) {")
//        )

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
     * @property requestClassName The [ClassName] for the generated request class.
     * @property responseClassName The [ClassName] for the generated response class.
     * @property asyncResponseClassName The [ClassName] for the generated async response class,
     *   or null when the method has no `asyncResponse` block.
     */
    data class MethodClassNames(
        val requestClassName: ClassName,
        val responseClassName: ClassName,
        val asyncResponseClassName: ClassName? = null,
    )

    /**
     * Generates the class names for a given [Method].
     *
     * @param method The method to generate class names for.
     * @return A [MethodClassNames] instance containing the request and response class names.
     */
    fun getClassNames(method: Method): MethodClassNames {
        val methodName = StringUtils.capitalize(method.name)
        val requestClassName = ClassName(requestsPackage, methodName + "Request")

        val responseClassName = method.response?.let { response ->
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

        val asyncResponseClassName = method.asyncResponse?.let { asyncResponse ->
            when (asyncResponse.typeSpec) {
                is DomainTypeSpec.ObjectSpec -> {
                    ClassName(requestsPackage, methodName + "AsyncResponse")
                }

                else -> null
            }
        }

        return MethodClassNames(requestClassName, responseClassName, asyncResponseClassName)
    }

    /**
     * Processes a single [Method] to generate its corresponding request and response classes.
     *
     * @param method The method to process.
     */
    fun processMethod(method: Method) {
        val (requestClassName, responseClassName, asyncResponseClassName) = getClassNames(method)
        // Path-only methods (no id) are not yet supported — see PROJECT.md Out of Scope
        val methodId = requireNotNull(method.id) {
            "Method '${method.name}' has no id — path-based dispatch is not yet supported in MethodProcessor"
        }

        val superClassName = getSuperClassName(method)
        val superClassType = superClassName.parameterizedBy(responseClassName)

        val requestClassBuilder = TypeSpec.classBuilder(requestClassName)
            .superclass(superClassType)
            .addSuperclassConstructorParameter("%L", methodId)
            .addModifiers(KModifier.PUBLIC)

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

            // make the constructor that the builder will use
            val constructorBuilder = FunSpec.constructorBuilder()

            val formatBuilder = StringBuilder("Params(")
            val parameterNames = ArrayList<String>()

            method.parameters.forEach { field ->
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

//                if (field.type is DomainTypeSpec.ObjectSpec) {
//                    val subObjectClassName = objectProcessor.getSubObjectClassName(requestClassName, field, false)
//                    val subObjectTypeSpec =
//                        objectProcessor.processObjectToTypeSpec(subObjectClassName, field.type, false)
////                if (forceTopLevel) {
////                    // TODO this could be more cleanly separated or parent method named - write vs process
////                    writeClassToFile(subObjectClassName, subObjectTypeSpec)
////                } else {
//                    requestClassBuilder.addType(subObjectTypeSpec)
////                }
//                }
            }
            formatBuilder.append(")")

            requestClassBuilder.addSuperclassConstructorParameter("params")

            requestClassBuilder.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("params", paramsClassName)
                    .build()
            )

            requestClassBuilder.addFunction(
                constructorBuilder
                    .callThisConstructor(CodeBlock.of(formatBuilder.toString(), *parameterNames.toTypedArray()))
                    .build()
            )

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

        requestClassBuilder.addFunction(
            FunSpec.builder("deserializeResponse")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("json", Json::class.asClassName())
                .addParameter("jsonElement", JsonElement::class.asClassName())
                .returns(responseClassName)
                .addCode("return json.%M(jsonElement)", decodeFromJsonElement)
                .build()
        )

        TypeSpec.interfaceBuilder(ClassName(requestClassName.packageName, requestClassName.simpleName, "RequestConfig"))

        val groupedParameters = method.parameters.groupBy { it.isRequired }
        val requiredParameters = groupedParameters[true]
        val optionalParameters = groupedParameters[false]

        val builder = FunSpec.builder("build")

        TypeSpec.companionObjectBuilder()
            .addFunction(
                builder.build()
            )

        val responseClassTypeSpec = createResponseClassDefinition(method.response, responseClassName)
        val asyncResponseClassTypeSpec = asyncResponseClassName?.let {
            createAsyncResponseClassDefinition(method.asyncResponse, it)
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

//    private fun processParamsForBuilder(
//        method: Method, requestClassName: ClassName, builderClassName: ClassName,
//        builderClassBuilder: TypeSpec.Builder,
//        useFutureProofEnum: Boolean
//    ) {
//        val booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil")
//        val futureProofEnumAccessor = ClassName.get(
//            FutureProofEnumAccessor::class.java
//        )
//        for (field in method.parameters) {
//            var className: ClassName? = null
//            if (field.getType() === DataType.OBJECT) {
//                className = ClassName.get(requestsPackage, requestClassName.simpleName(), getFieldClassName(field))
//            } else if (field.getType() === DataType.ENUM) {
//                className = ClassName.get(requestsPackage, requestClassName.simpleName(), getFieldClassName(field))
//            } else if (field.getType() === DataType.ARRAY && field.getItems().getType() === DataType.OBJECT) {
//                className = ClassName.get(requestsPackage, requestClassName.simpleName(), getFieldClassName(field))
//            }
//            val type: DataType<*> = getEffectiveFieldType(field)
//            if (type === DataType.ENUM && useFutureProofEnum) {
//                val typeName: TypeName = getTypeName(field, field.isRequired(), true, false)
//                val setterParameter = ParameterSpec.builder(typeName, field.getLongName())
//                if (!field.isRequired() && field.getDefaultValue() == null) {
//                    setterParameter.addAnnotation(com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION)
//                }
//                val setterBuilder = MethodSpec.methodBuilder(
//                    "set" + mJavaTool.fieldNameToClassStyle(field.getLongName())
//                )
//                    .addModifiers(Modifier.PUBLIC)
//                    .addParameter(setterParameter.build())
//                setterBuilder.addStatement("this.\$N.setEnumValue(\$N)", field.getLongName(), field.getLongName())
//                setterBuilder.addStatement("return this;")
//                builderClassBuilder.addFunction(setterBuilder.build())
//            } else {
//                val typeName: TypeName = getTypeName(field, field.isRequired(), true, useFutureProofEnum, className)
//                val setterParameter = ParameterSpec.builder(typeName, field.getLongName())
//                val convertToBoolean = field.getType() === DataType.INT && interpretedAsBoolean(field)
//                if (!field.isRequired() && field.getDefaultValue() == null) {
//                    setterParameter.addAnnotation(com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION)
//                }
//                val setterBuilder = MethodSpec.methodBuilder(
//                    StringUtils.uncapitalize(
//                        mJavaTool.fieldNameToClassStyle(
//                            field.getLongName()
//                        )
//                    )
//                )
//                    .addModifiers(Modifier.PUBLIC)
//                    .returns(builderClassName)
//                    .addParameter(setterParameter.build())
//                if (convertToBoolean) {
//                    if (field.isRequired()) {
//                        setterBuilder.addStatement(
//                            "this.params.\$N = \$T.convertToInt(\$N)",
//                            field.getLongName(),
//                            booleanUtil,
//                            field.getLongName()
//                        )
//                    } else {
//                        setterBuilder.addStatement(
//                            "this.params.\$N = \$T.convertToInteger(\$N)",
//                            field.getLongName(),
//                            booleanUtil,
//                            field.getLongName()
//                        )
//                    }
//                } else {
//                    setterBuilder.addStatement("this.params.\$N = \$N", field.getLongName(), field.getLongName())
//                }
//                setterBuilder.addStatement("return this")
//                builderClassBuilder.addFunction(setterBuilder.build())
//            }
//        }
//    }
//
//    private fun processParams(method: Method, requestClassName: ClassName, paramsBuilder: TypeSpec.Builder) {
//        val useFutureProofEnum = false
//        val booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil")
//        val futureProofEnumAccessor = ClassName.get(
//            FutureProofEnumAccessor::class.java
//        )
//        for (field in method.parameters) {
//            var className: ClassName? = null
//            if (field.getType() === DataType.OBJECT) {
//                className = requestClassName.nestedClass(getFieldClassName(field))
//            } else if (field.getType() === DataType.ENUM) {
//                className = requestClassName.nestedClass(getFieldClassName(field))
//            } else if (field.getType() === DataType.ARRAY && field.getItems().getType() === DataType.OBJECT) {
//                className = requestClassName.nestedClass(getFieldClassName(field))
//            }
//            try {
//                val fieldSpec: FieldSpec = createFieldSpec(
//                    field,
//                    com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION,
//                    useFutureProofEnum,
//                    className,
//                    false
//                )
//                paramsBuilder.addField(fieldSpec)
//            } catch (e: Exception) {
//                throw IllegalStateException(
//                    java.lang.String.format(
//                        "Error processing field %s in %s",
//                        field.getLongName(),
//                        method.name
//                    ), e
//                )
//            }
//            val type: DataType<*> = getEffectiveFieldType(field)
//            if (type === DataType.ENUM && useFutureProofEnum) {
//                val typeName: TypeName = getTypeName(field, field.isRequired(), true, false, className)
//                val setterParameter = ParameterSpec.builder(typeName, field.getLongName())
//                var getterBuilder = MethodSpec.methodBuilder(
//                    "get" + mJavaTool.fieldNameToClassStyle(field.getLongName())
//                )
//                    .returns(typeName)
//                    .addModifiers(Modifier.PUBLIC)
//                getterBuilder.addStatement("return \$N.getEnumValue()", field.getLongName())
//                if (!field.isRequired() && field.getDefaultValue() == null) {
//                    getterBuilder.addAnnotation(com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION)
//                    setterParameter.addAnnotation(com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION)
//                }
//                paramsBuilder.addFunction(getterBuilder.build())
//                val setterBuilder = MethodSpec.methodBuilder(
//                    "set" + mJavaTool.fieldNameToClassStyle(field.getLongName())
//                )
//                    .addModifiers(Modifier.PUBLIC)
//                    .addParameter(setterParameter.build())
//                setterBuilder.addStatement("this.\$N.setEnumValue(\$N)", field.getLongName(), field.getLongName())
//                paramsBuilder.addFunction(setterBuilder.build())
//                getterBuilder = MethodSpec.methodBuilder(
//                    "getFutureProof" + mJavaTool.fieldNameToClassStyle(field.getLongName())
//                )
//                    .returns(ParameterizedTypeName.get(futureProofEnumAccessor, typeName))
//                    .addModifiers(Modifier.PUBLIC)
//                getterBuilder.addStatement("return \$N.asReadOnly()", field.getLongName())
//                paramsBuilder.addFunction(getterBuilder.build())
//            } else {
//                val typeName: TypeName = getTypeName(field, field.isRequired(), true, useFutureProofEnum, className)
//                val setterParameter = ParameterSpec.builder(typeName, field.getLongName())
//                val getterBuilder = MethodSpec.methodBuilder(
//                    "get" + mJavaTool.fieldNameToClassStyle(field.getLongName())
//                )
//                    .returns(typeName)
//                    .addModifiers(Modifier.PUBLIC)
//                val convertToBoolean = field.getType() === DataType.INT && interpretedAsBoolean(field)
//                if (convertToBoolean) {
//                    getterBuilder.addStatement("return \$T.convertToBoolean(\$N)", booleanUtil, field.getLongName())
//                } else {
//                    getterBuilder.addStatement("return \$N", field.getLongName())
//                }
//                if (!field.isRequired() && field.getDefaultValue() == null) {
//                    getterBuilder.addAnnotation(com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION)
//                    setterParameter.addAnnotation(com.giffardtechnologies.restdocs.JavaGenerator.NULLABLE_ANNOTATION)
//                }
//                paramsBuilder.addFunction(getterBuilder.build())
//                val setterBuilder = MethodSpec.methodBuilder(
//                    "set" + mJavaTool.fieldNameToClassStyle(field.getLongName())
//                )
//                    .addModifiers(Modifier.PUBLIC)
//                    .addParameter(setterParameter.build())
//                if (convertToBoolean) {
//                    if (field.isRequired()) {
//                        setterBuilder.addStatement(
//                            "this.\$N = \$T.convertToInt(\$N)",
//                            field.getLongName(),
//                            booleanUtil,
//                            field.getLongName()
//                        )
//                    } else {
//                        setterBuilder.addStatement(
//                            "this.\$N = \$T.convertToInteger(\$N)",
//                            field.getLongName(),
//                            booleanUtil,
//                            field.getLongName()
//                        )
//                    }
//                } else {
//                    setterBuilder.addStatement("this.\$N = \$N", field.getLongName(), field.getLongName())
//                }
//                paramsBuilder.addFunction(setterBuilder.build())
//            }
//        }
//    }

    // origin commented out
//		private void processParamsForConstructor(Method method, ClassName requestClassName, MethodSpec.Builder builder) {
    //			boolean useFutureProofEnum = false;
    //
    //			ClassName booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil");
    //			ClassName futureProofEnumAccessor = ClassName.get(FutureProofEnumAccessor.class);
    //
    //			for (Field field : method.getParameters()) {
    //				ClassName className = null;
    //				if (field.getType() == DataType.OBJECT) {
    //					className = requestClassName.nestedClass(getFieldClassName(field));
    //				} else if (field.getType() == DataType.ENUM) {
    //					className = requestClassName.nestedClass(getFieldClassName(field));
    //				} else if (field.getType() == DataType.ARRAY && field.getItems().getType() == DataType.OBJECT) {
    //					className = requestClassName.nestedClass(getFieldClassName(field));
    //				}
    //
    //				try {
    //					FieldSpec fieldSpec = createFieldSpec(field, NULLABLE_ANNOTATION, useFutureProofEnum, className, false);
    //					paramsBuilder.addField(fieldSpec);
    //				} catch (Exception e) {
    //					throw new IllegalStateException(String.format("Error processing field %s in %s",
    //					                                              field.getLongName(),
    //					                                              method.getName()), e);
    //				}
    //
    //				DataType type = getEffectiveFieldType(field);
    //				if (type == DataType.ENUM && useFutureProofEnum) {
    //					TypeName typeName = getTypeName(field, field.isRequired(), true, false, className);
    //					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.getLongName());
    //
    //					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
    //							                                             "get" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
    //					                                             .returns(typeName)
    //					                                             .addModifiers(Modifier.PUBLIC);
    //					getterBuilder.addStatement("return $N.getEnumValue()", field.getLongName());
    //					if (!field.isRequired() && field.getDefaultValue() == null) {
    //						getterBuilder.addAnnotation(NULLABLE_ANNOTATION);
    //						setterParameter.addAnnotation(NULLABLE_ANNOTATION);
    //					}
    //					paramsBuilder.addFunction(getterBuilder.build());
    //
    //					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
    //							                                             "set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
    //					                                             .addModifiers(Modifier.PUBLIC)
    //					                                             .addParameter(setterParameter.build());
    //					setterBuilder.addStatement("this.$N.setEnumValue($N)", field.getLongName(), field.getLongName());
    //					paramsBuilder.addFunction(setterBuilder.build());
    //
    //					getterBuilder = MethodSpec.methodBuilder(
    //							                          "getFutureProof" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
    //					                          .returns(ParameterizedTypeName.get(futureProofEnumAccessor, typeName))
    //					                          .addModifiers(Modifier.PUBLIC);
    //					getterBuilder.addStatement("return $N.asReadOnly()", field.getLongName());
    //					paramsBuilder.addFunction(getterBuilder.build());
    //				} else {
    //					TypeName typeName = getTypeName(field, field.isRequired(), true, useFutureProofEnum, className);
    //					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.getLongName());
    //
    //					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
    //							                                             "get" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
    //					                                             .returns(typeName)
    //					                                             .addModifiers(Modifier.PUBLIC);
    //
    //					boolean convertToBoolean = field.getType() == DataType.INT && hasBooleanRestriction(field);
    //					if (convertToBoolean) {
    //						getterBuilder.addStatement("return $T.convertToBoolean($N)", booleanUtil, field.getLongName());
    //					} else {
    //						getterBuilder.addStatement("return $N", field.getLongName());
    //					}
    //
    //					if (!field.isRequired() && field.getDefaultValue() == null) {
    //						getterBuilder.addAnnotation(NULLABLE_ANNOTATION);
    //						setterParameter.addAnnotation(NULLABLE_ANNOTATION);
    //					}
    //
    //					paramsBuilder.addFunction(getterBuilder.build());
    //
    //					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
    //							                                             "set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
    //					                                             .addModifiers(Modifier.PUBLIC)
    //					                                             .addParameter(setterParameter.build());
    //
    //					if (convertToBoolean) {
    //						if (field.isRequired()) {
    //							setterBuilder.addStatement("this.$N = $T.convertToInt($N)",
    //							                           field.getLongName(),
    //							                           booleanUtil,
    //							                           field.getLongName());
    //						} else {
    //							setterBuilder.addStatement("this.$N = $T.convertToInteger($N)",
    //							                           field.getLongName(),
    //							                           booleanUtil,
    //							                           field.getLongName());
    //						}
    //					} else {
    //						setterBuilder.addStatement("this.$N = $N", field.getLongName(), field.getLongName());
    //					}
    //
    //					paramsBuilder.addFunction(setterBuilder.build());
    //				}
    //			}
    //		}
}
