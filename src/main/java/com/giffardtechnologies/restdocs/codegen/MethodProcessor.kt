package com.giffardtechnologies.restdocs.codegen

import com.allego.meter.file
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import org.apache.commons.lang3.StringUtils
import java.io.File
import com.giffardtechnologies.restdocs.domain.type.TypeSpec as DomainTypeSpec


class MethodProcessor(
    private val codeDirectory: File,
    private val requestsPackage: String,
    private val typeRefPackage: String,
    private val fieldAndTypeProcessor: FieldAndTypeProcessor,
    private val usePath: Boolean = false,
) {

    private val mAuthenticatedAllegoRequestClassName = ClassName(
        "$requestsPackage.support",
        "AllegoAuthenticatedRequest"
    )

    private val mAllegoRequestClassName = ClassName(
        "$requestsPackage.support",
        "AllegoOpenRequest"
    )
    private val mAllegoPathAndBodyRequestClassName: ClassName = ClassName(
        "$requestsPackage.support",
        "AllegoBodyStackRequest"
    )

    private val objectProcessor: ObjectProcessor = ObjectProcessor(codeDirectory, fieldAndTypeProcessor)

    private data class ResponseClassDefinition(val className: ClassName, val typeSpec: TypeSpec? = null)

    fun processMethod(method: Method) {
        val methodName = StringUtils.capitalize(method.name)

        val responseClassDefinition = createResponseClassDefinition(method.response, methodName)

        val requestClassNameStr = methodName + "Request"
        val requestClassName = ClassName(requestsPackage, requestClassNameStr)
        val baseClassName = if (method.isAuthenticationRequired) {
            mAuthenticatedAllegoRequestClassName
        } else {
            if (usePath && method.id == null) {
                mAllegoPathAndBodyRequestClassName
            } else {
                mAllegoRequestClassName
            }
        }

        val superClassType = baseClassName.parameterizedBy(
            responseClassDefinition.className
        )

        val requestClassBuilder = TypeSpec.classBuilder(requestClassName)
            .superclass(superClassType)
            .addSuperclassConstructorParameter("%L", method.id!!)
            .addModifiers(KModifier.PUBLIC)

        val paramsClassName = if (method.parameters.isEmpty) {
            requestClassBuilder.addSuperclassConstructorParameter("%N", "Unit")
            Unit::class.asClassName()
        } else {
            ClassName(
                requestsPackage,
                requestClassNameStr,
                "Params"
            )
        }

        if (!method.parameters.isEmpty) {
            // make the constructor that the builder will use
            val constructorBuilder = FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE)

            val formatBuilder = StringBuilder("Params(")
            val parameterNames = ArrayList<String>()

            constructorBuilder.addParameter("params", paramsClassName)
            method.parameters.forEach { field ->
                // largely copied code from ObjectProcessor
                val propertySpec = fieldAndTypeProcessor.createPropertySpec(field, false)
                if (!propertySpec.type.isNullable) {
                    constructorBuilder.addParameter(field.longName, propertySpec.type)
                } else {
                    constructorBuilder.addParameter(field.longName, propertySpec.type)
                }
                formatBuilder.append("%N")
                parameterNames.add(field.longName)

                if (field.type is DomainTypeSpec.ObjectSpec) {
                    val subObjectClassName = objectProcessor.getSubObjectClassName(requestClassName, field, false)
                    val subObjectTypeSpec =
                        objectProcessor.processObjectToTypeSpec(subObjectClassName, field.type, false)
//                if (forceTopLevel) {
//                    // TODO this could be more cleanly separated or parent method named - write vs process
//                    writeClassToFile(subObjectClassName, subObjectTypeSpec)
//                } else {
                    requestClassBuilder.addType(subObjectTypeSpec)
//                }
                }
            }
            formatBuilder.append(")")

            requestClassBuilder.addSuperclassConstructorParameter(formatBuilder.toString(), *parameterNames.toTypedArray())

            requestClassBuilder.primaryConstructor(constructorBuilder.build())
        }

//        if (method.parameters.isEmpty) {
//            addMinimalConstructor(method, requestClassName, requestClassBuilder)
//        } else {
//            if (method.parameters.size() < 3) {
//                addMinimalConstructor(method, requestClassName, requestClassBuilder)
//            }
//
//            // prepare the params data object
//            val paramsObjectPackage = "$requestsPackage.$requestClassNameStr"
////            for (field in method.getParameters()) {
////                if (field.getLongName().equalsIgnoreCase(method.name + "id")) {
////                    field.setLongName("id")
////                }
////            }
//
//            TypeSpec.interfaceBuilder(ClassName(requestClassName.packageName, requestClassName.simpleName, "RequestConfig"))
//
//            val groupedParameters = method.parameters.groupBy { it.isRequired }
//            val requiredParameters = groupedParameters[true]
//            val optionalParameters = groupedParameters[false]
//
//            val builder = FunSpec.builder("build")
//
//            TypeSpec.companionObjectBuilder()
//                .addFunction(
//                    builder.build()
//                )
//
//            requestClassBuilder.addType()

//            // build the Params inner class
//            val copyableClassName = ClassName.get("com.allego.api.client2.requests.support", "Copyable")
//            val paramsSuperClass = ParameterizedTypeName.get(copyableClassName, paramsClassName)
//            val paramsBuilder = TypeSpec.classBuilder(paramsClassName)
//                .superclass(paramsSuperClass)
//                .addSuperinterface(Cloneable::class.java)
//                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
//            processParams(method, requestClassName, paramsBuilder)
//            requestClassBuilder.addType(paramsBuilder.build())
//            for (field in method.parameters) {
//                var typeSpec: TypeSpec? = null
//                if (field.getType() === DataType.OBJECT) {
//                    val fieldDataObject = DataObject()
//                    fieldDataObject.setFields(field.getFields())
//                    fieldDataObject.setName(getFieldClassName(field))
//                    typeSpec = paramsDataObjectProcessor.processDataObjectToBuilder(fieldDataObject)
//                        .addModifiers(Modifier.STATIC)
//                        .build()
//                }
//                if (field.getType() === DataType.ENUM) {
//                    val fieldEnumeration = NamedEnumeration()
//                    fieldEnumeration.setValues(field.getValues())
//                    fieldEnumeration.setKey(field.getKey())
//                    fieldEnumeration.setName(getFieldClassName(field))
//                    typeSpec = processEnumToTypeSpec(fieldEnumeration, paramsObjectPackage, false)
//                }
//                if (field.getType() === DataType.ARRAY && field.getItems().getType() === DataType.OBJECT) {
//                    val fieldDataObject = DataObject()
//                    fieldDataObject.setFields(field.getItems().getFields())
//                    fieldDataObject.setName(getFieldClassName(field))
//                    typeSpec = paramsDataObjectProcessor.processDataObjectToBuilder(fieldDataObject)
//                        .addModifiers(Modifier.STATIC)
//                        .build()
//                }
//                if (typeSpec != null) {
//                    requestClassBuilder.addType(typeSpec)
//                }
//            }
//        }

        file(requestClassName) {
            addType(requestClassBuilder.build())
            responseClassDefinition.typeSpec?.let {
                addType(responseClassDefinition.typeSpec)
            }
        }
            .writeTo(codeDirectory)
    }

    private fun createResponseClassDefinition(
        response: Response?,
        methodName: String
    ): ResponseClassDefinition {
        return response?.let {
            when (response.typeSpec) {
                is DomainTypeSpec.TypeRefSpec -> {
                    ResponseClassDefinition(ClassName(typeRefPackage, response.typeSpec.referenceName))
                }

                is DomainTypeSpec.ObjectSpec -> {
                    // TODO old - this need to handle imports for type refs
                    val className = ClassName(requestsPackage, methodName + "Response")
                    ResponseClassDefinition(
                        className,
                        objectProcessor.processObjectToTypeSpec(
                            className,
                            response.typeSpec,
                            useFutureProofEnum = true,
                            forceTopLevel = false,
                        )
                    )
                }

                else -> null
            }
        } ?: ResponseClassDefinition(Unit::class.asClassName())
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

