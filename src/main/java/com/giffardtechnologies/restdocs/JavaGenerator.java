package com.giffardtechnologies.restdocs;

import com.allego.util.futureproofenum.FutureProofEnumAccessor;
import com.allego.util.futureproofenum.IntId;
import com.allego.util.futureproofenum.LongId;
import com.allego.util.futureproofenum.StringId;
import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.Method;
import com.giffardtechnologies.restdocs.domain.NamedEnumeration;
import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.EnumConstant;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.KeyType;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.giffardtechnologies.json.gson.BooleanDeserializer;
import org.giffardtechnologies.json.gson.LowercaseEnumTypeAdapterFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(description = "Generates java code based for a given API descriptor", name = "java_generator", mixinStandardHelpOptions = true, version = "1.0")
public class JavaGenerator implements Callable<Void> {
	private static final Logger sLogger = LoggerFactory.getLogger(JavaGenerator.class);

	private static final String FUTURE_PROOF_ENUM_PACKAGE = "com.allego.util.futureproofenum";

	//	@Parameters(index = "0", hidden = true, description = "The executable directory, passed by the wrapper script.")
//	private File mExecutableDir;

	@Option(names = {"-f", "-p", "--properties"}, description = "The properties file describing the generation.")
	private File mPropertiesFile;

	private File mSourceFile;

	private Properties mProperties;
	private File mCodeDir;
	private CodeFormatter mCodeFormatter;
	private Document mDocument;
	private JavaTool mJavaTool;

	public static void main(String[] args) {
//		for (String arg : args) {
//			System.out.print(arg);
//			System.out.print(", ");
//		}
//		System.out.println();
		JavaGenerator docGenerator = new JavaGenerator();

		CommandLine.call(docGenerator, args);
	}

	@Override
	public Void call() throws Exception {
//		System.out.println(System.getProperty("execdir"));
//		System.out.println(System.getProperty("progdir"));
//		mExecutableDir = new File(System.getProperty("execdir"));
		if (mPropertiesFile == null) {
			mPropertiesFile = new File("javabuild.properties");
		}

		mPropertiesFile = mPropertiesFile.getAbsoluteFile();

		mProperties = new Properties();
		BufferedInputStream propsInStream = new BufferedInputStream(new FileInputStream(mPropertiesFile));

		mProperties.load(propsInStream);
		propsInStream.close();

		setSourceFile(new File(mPropertiesFile.getParentFile(), mProperties.getProperty("sourceFile")));

		generateCode();

		return null;
	}

	public void setSourceFile(File sourceFile) {
		mSourceFile = sourceFile;
	}

	private void generateCode() throws IOException {
		mDocument = parseDocument();
		mJavaTool = new JavaTool(mDocument);

		mCodeDir = new File(mProperties.getProperty("codeDir"));
		// TODO check this exists

		String dtoPackage = mProperties.getProperty("dtoPackage");

		initCodeFormatter();

		List<DataObject> dataObjects;
		if (mProperties.containsKey("javagen.includeDataObjects")) {
			Set<String> includeDataObjects = new HashSet<>(Arrays.asList(mProperties.getProperty("javagen.includeDataObjects")
			                                                                        .split(",")));
			dataObjects = mDocument.getDataObjects()
			                       .stream()
			                       .filter(dataObject -> includeDataObjects.contains(dataObject.getName()))
			                       .collect(Collectors.toList());
		} else {
			dataObjects = mDocument.getDataObjects();
		}

		DataObjectProcessor dataObjectProcessor = new DataObjectProcessor(dtoPackage, dtoPackage);

		for (DataObject dataObject : dataObjects) {
			dataObjectProcessor.processDataObject(dataObject);
		}

		List<NamedEnumeration> enumerations;
		if (mProperties.containsKey("javagen.includeEnums")) {
			Set<String> includeDataObjects = new HashSet<>(Arrays.asList(mProperties.getProperty("javagen.includeEnums")
			                                                                        .split(",")));
			enumerations = mDocument.getEnumerations()
			                       .stream()
			                       .filter(namedEnumeration -> includeDataObjects.contains(namedEnumeration.getName()))
			                       .collect(Collectors.toList());
		} else {
			enumerations = mDocument.getEnumerations();
		}
		for (NamedEnumeration namedEnumeration : enumerations) {
			processEnum(namedEnumeration, dtoPackage);
		}

		if (mDocument.getService() != null) {
			// create the requests & responses
			String responsePackage = mProperties.getProperty("responsePackage");
			String requestPackage = mProperties.getProperty("requestPackage");

			List<Method> methods;
			if (mProperties.containsKey("javagen.includeMethodIDs")) {
				Set<Integer> includeDataObjects = Arrays.stream(mProperties.getProperty("javagen.includeMethodIDs").split(","))
				                                        .map(Integer::parseInt)
				                                        .collect(Collectors.toSet());
				methods = mDocument.getService().getMethods()
				                       .stream()
				                       .filter(method -> includeDataObjects.contains(method.getId()))
				                       .collect(Collectors.toList());
			} else {
				methods = mDocument.getService().getMethods();
			}

			for (Method method : methods) {
				processMethod(method, requestPackage, responsePackage, dtoPackage);
			}
		}
	}

	private void processMethod(Method method, String requestPackage, String responsePackage, String dtoPackage) {
		DataObjectProcessor dataObjectProcessor = new DataObjectProcessor(responsePackage, dtoPackage);
		String methodName = StringUtils.capitalize(method.getName());

		String responseClassName = null;
		if (method.getResponse() != null) {
			if (method.getResponse().getType() == DataType.OBJECT) {
				responseClassName = methodName + "Response";

				// setup a temp DataObject
				DataObject dataObject = new DataObject();
				dataObject.setName(responseClassName);
				dataObject.setFields(method.getResponse().getFields());

				// TODO this need to handle imports for type refs
				dataObjectProcessor.processDataObject(dataObject);
			}
		}

		String requestClassNameStr = methodName + "Request";
		ClassName requestClassName = ClassName.get(requestPackage, requestClassNameStr);
		ClassName authenticatedAllegoRequestClassName = ClassName.get(requestPackage + ".support",
		                                                              "AuthenticatedAllegoRequest");

		ClassName methodIDAnnotation = ClassName.get("com.allego.api.client2.requests.support", "MethodID");
		ClassName methodIDsClass = ClassName.get("com.allego.api.client2.requests.support", "MethodIds");
		String methodConstant = method.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
		AnnotationSpec methodAnnotation = AnnotationSpec.builder(methodIDAnnotation)
		                                                .addMember("id", "$T.$N", methodIDsClass, methodConstant)
		                                                .build();

		TypeName paramsClassName = method.getParameters().isEmpty() ? TypeName.get(Void.class) : ClassName.get(
				requestPackage + "." + requestClassNameStr,
				"Params");
		TypeName responseTypeName =
				responseClassName == null ? TypeName.get(Void.class) : ClassName.get(responsePackage,
				                                                                     responseClassName);
		ParameterizedTypeName superClassParamed = ParameterizedTypeName.get(authenticatedAllegoRequestClassName,
		                                                                    paramsClassName,
		                                                                    responseTypeName);

		TypeSpec.Builder builder = TypeSpec.classBuilder(requestClassName)
		                                   .superclass(superClassParamed)
		                                   .addAnnotation(methodAnnotation)
		                                   .addModifiers(Modifier.PUBLIC);

		ClassName futureProofEnumContainer = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE, "FutureProofEnumContainer");
		AnnotationSpec nullableAnnotation = AnnotationSpec.builder(Nullable.class).build();

		if (method.getParameters().isEmpty()) {
			builder.addMethod(MethodSpec.constructorBuilder()
			                            .addModifiers(Modifier.PUBLIC)
			                            .addParameter(String.class, "accessKey")
			                            .addStatement("super($N, null)", "accessKey")
//			                            .addJavadoc(CodeBlock.builder()
//			                                                 .add("Creates a request that \n\n\n\n " +
//					                                                      method.getDescription())
//			                                                 .add(" @param accessKey the access key to use for the request")
//			                                                 .build())
			                            .build());
		}

		MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder("getDummy")
		                                             .returns(Long.class)
		                                             .addModifiers(Modifier.PUBLIC);

		getterBuilder.addStatement("return null");
		getterBuilder.addAnnotation(nullableAnnotation);
		builder.addMethod(getterBuilder.build());

//		for (Field field : method.getParameters()) {
//			try {
//				FieldSpec fieldSpec = createFieldSpec(field, futureProofEnumContainer, nullableAnnotation);
//				builder.addField(fieldSpec);
//			} catch (Exception e) {
//				throw new IllegalStateException(String.format("Error processing field %s in %s",
//				                                              field.getLongName(),
//				                                              dataObject.getName()), e);
//			}
//		}
//
//		ClassName booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil");
//		ClassName futureProofEnumAccessor = ClassName.get(FutureProofEnumAccessor.class);
//
//		for (Field field : dataObject.getFields()) {
//			DataType type = getEffectiveFieldType(field);
//			if (type == DataType.ENUM) {
//				TypeName typeName = getTypeName(field, field.isRequired(), true, false);
//				ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.getLongName());
//
//				MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
//						"get" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//				                                             .returns(typeName)
//				                                             .addModifiers(Modifier.PUBLIC);
//				getterBuilder.addStatement("return $N.getEnumValue()", field.getLongName());
//				if (!field.isRequired() && field.getDefaultValue() == null) {
//					getterBuilder.addAnnotation(nullableAnnotation);
//					setterParameter.addAnnotation(nullableAnnotation);
//				}
//				builder.addMethod(getterBuilder.build());
//
//				MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
//						"set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//				                                             .addModifiers(Modifier.PUBLIC)
//				                                             .addParameter(setterParameter.build());
//				setterBuilder.addStatement("this.$N.setEnumValue($N)", field.getLongName(), field.getLongName());
//				builder.addMethod(setterBuilder.build());
//
//				getterBuilder = MethodSpec.methodBuilder(
//						"getFutureProof" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//				                          .returns(ParameterizedTypeName.get(futureProofEnumAccessor, typeName))
//				                          .addModifiers(Modifier.PUBLIC);
//				getterBuilder.addStatement("return $N.asReadOnly()", field.getLongName());
//				builder.addMethod(getterBuilder.build());
//			} else {
//				TypeName typeName = getTypeName(field, field.isRequired(), true);
//				ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.getLongName());
//
//				MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
//						"get" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//				                                             .returns(typeName)
//				                                             .addModifiers(Modifier.PUBLIC);
//
//				boolean convertToBoolean = field.getType() == DataType.INT && hasBooleanRestriction(field);
//				if (convertToBoolean) {
//					getterBuilder.addStatement("return $T.convertToBoolean($N)", booleanUtil, field.getLongName());
//				} else {
//					getterBuilder.addStatement("return $N", field.getLongName());
//				}
//
//				if (!field.isRequired() && field.getDefaultValue() == null) {
//					getterBuilder.addAnnotation(nullableAnnotation);
//					setterParameter.addAnnotation(nullableAnnotation);
//				}
//
//				builder.addMethod(getterBuilder.build());
//
//				MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
//						"set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//				                                             .addModifiers(Modifier.PUBLIC)
//				                                             .addParameter(setterParameter.build());
//
//				if (convertToBoolean) {
//					if (field.isRequired()) {
//						setterBuilder.addStatement("this.$N = $T.convertToInt($N)",
//						                           field.getLongName(),
//						                           booleanUtil,
//						                           field.getLongName());
//					} else {
//						setterBuilder.addStatement("this.$N = $T.convertToInteger($N)",
//						                           field.getLongName(),
//						                           booleanUtil,
//						                           field.getLongName());
//					}
//				} else {
//					setterBuilder.addStatement("this.$N = $N", field.getLongName(), field.getLongName());
//				}
//
//				builder.addMethod(setterBuilder.build());
//			}
//		}

//		builder.addMethod(MethodSpec.constructorBuilder()
//		                            .addModifiers(Modifier.PUBLIC)
//		                            .addParameter(int.class, "id")
//		                            .addStatement("$N = $N",
//				                            "this.id",
//				                            "id")
//		                            .build());
//
//		builder.addMethod(MethodSpec.methodBuilder("getId")
//		                            .returns(int.class)
//		                            .addModifiers(Modifier.PUBLIC)
//		                            .addStatement("return $N",
//				                            "id")
//		                            .build());

		writeFormattedClassFile(requestPackage, builder);

	}

	private void initCodeFormatter() {
		// take default Eclipse formatting options
		@SuppressWarnings("unchecked") Map<String, Object> options = DefaultCodeFormatterConstants.getEclipseDefaultSettings();

		// initialize the compiler settings to be able to format 1.5 code
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);

		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, 0);

		// change the option to wrap each enum constant on a new line
		options.put(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ENUM_CONSTANTS,
		            DefaultCodeFormatterConstants.createAlignmentValue(true,
		                                                               DefaultCodeFormatterConstants.WRAP_ONE_PER_LINE,
		                                                               DefaultCodeFormatterConstants.INDENT_DEFAULT));

		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_JAVADOC_COMMENT, DefaultCodeFormatterConstants.TRUE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_BLOCK_COMMENT, DefaultCodeFormatterConstants.TRUE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_LINE_LENGTH, 40);
		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_CLEAR_BLANK_LINES_IN_JAVADOC_COMMENT, DefaultCodeFormatterConstants.TRUE);

		options.put(DefaultCodeFormatterConstants.FORMATTER_KEEP_ANNOTATION_DECLARATION_ON_ONE_LINE, DefaultCodeFormatterConstants.ONE_LINE_ALWAYS);

		options.put(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION, DefaultCodeFormatterConstants.NEXT_LINE_ON_WRAP);
//		options.put(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_CONSTRUCTOR_DECLARATION, DefaultCodeFormatterConstants.NEXT_LINE);

		// instantiate the default code formatter with the given options
		mCodeFormatter = ToolFactory.createCodeFormatter(options);
	}

	private Document parseDocument() throws IOException {
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(mSourceFile));
		Yaml yaml = new Yaml();
		Map<String, Object> map = yaml.load(input);
		input.close();

		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory(true))
		           .registerTypeAdapter(boolean.class, new BooleanDeserializer())
		           .setPrettyPrinting();

		Gson gson = gsonBuilder.create();
		String json = gson.toJson(map);
		Document document = gson.fromJson(json, Document.class);

		document.buildMappings();

		return document;
	}

	private void processEnum(NamedEnumeration namedEnumeration, String enumPackage) {
		ClassName enumClassName = ClassName.get(enumPackage, namedEnumeration.getName());

		TypeSpec.Builder builder = TypeSpec.enumBuilder(enumClassName).addModifiers(Modifier.PUBLIC);

		ClassName futureProofAnnotation = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE, "FutureProof");
		ClassName futureProofUnknownAnnotation = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE, "Unknown");

		switch (namedEnumeration.getKey()) {
			case INT:
				builder.addSuperinterface(ClassName.get(IntId.class)).addAnnotation(futureProofAnnotation);
				break;
			case LONG:
				builder.addSuperinterface(ClassName.get(LongId.class));
				break;
			case STRING:
				builder.addSuperinterface(ClassName.get(StringId.class));
				break;
			case ENUM:
				break;
		}
		builder.addEnumConstant("UNKNOWN",
		                        TypeSpec.anonymousClassBuilder("$L", -1)
		                                .addAnnotation(futureProofUnknownAnnotation)
		                                .build());
		for (EnumConstant enumConstant : namedEnumeration.getValues()) {
			builder.addEnumConstant(enumConstant.getLongName().toUpperCase().replace('-', '_'),
			                        TypeSpec.anonymousClassBuilder("$L", enumConstant.getValue()).build());
		}

		FieldSpec.Builder idFieldBuilder = FieldSpec.builder(int.class, "id")
		                                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL);
		builder.addField(idFieldBuilder.build());

		builder.addMethod(MethodSpec.constructorBuilder()
		                            .addParameter(int.class, "id")
		                            .addStatement("$N = $N", "this.id", "id")
		                            .build());

		builder.addMethod(MethodSpec.methodBuilder("getId")
		                            .returns(int.class)
		                            .addModifiers(Modifier.PUBLIC)
		                            .addStatement("return $N", "id")
		                            .build());

		writeFormattedClassFile(enumPackage, builder);
	}

	private void writeFormattedClassFile(String packageName, TypeSpec.Builder builder) {
		try {
			StringBuilder source = new StringBuilder();
			JavaFile javaFile = JavaFile.builder(packageName, builder.build()).indent("    ").build();
			javaFile.writeTo(source);

			JavaFileObject javaFileObject = javaFile.toJavaFileObject();


			final TextEdit edit = mCodeFormatter.format(CodeFormatter.K_COMPILATION_UNIT, // format a compilation unit
			                                            source.toString(), // source to format
			                                            0, // starting position
			                                            source.length(), // length
			                                            0, // initial indentation
			                                            System.getProperty("line.separator") // line separator
			);

			IDocument document = new org.eclipse.jface.text.Document(source.toString());
			edit.apply(document);

			File javaFilePath = new File(mCodeDir, javaFileObject.getName());
			javaFilePath.getParentFile().mkdirs();
			FileWriter fileWriter = new FileWriter(javaFilePath);
			fileWriter.append(String.format(
					"/*\n" + " * ===========================================================================\n" +
							" * Copyright (c) 2016-%d, Allego Corporation, MA USA\n" + " *\n" +
							" * This file and its contents are proprietary and confidential to and the sole\n" +
							" * intellectual property of Allego Corporation.  Any use, reproduction,\n" +
							" * redistribution or modification of this file is prohibited except as\n" +
							" * explicitly defined by written license agreement with Allego Corporation.\n" +
							" * ===========================================================================\n" +
							" */\n", 2020));
			fileWriter.append(document.get());
			fileWriter.close();

		} catch (MalformedTreeException | BadLocationException | IOException e) {
			e.printStackTrace();
			sLogger.error("Failed to write class file", e);
		}
	}

	private class DataObjectProcessor {

		private final String mObjectPackage;
		private final String mTypeRefPackage;

		private DataObjectProcessor(String objectPackage, String typeRefPackage) {
			mObjectPackage = objectPackage;
			mTypeRefPackage = typeRefPackage;
		}

		private void processDataObject(DataObject dataObject) {
			for (Field field : dataObject.getFields()) {
				if (field.getLongName().equalsIgnoreCase(dataObject.getName() + "id")) {
					field.setLongName("id");
				}
				if (field.getType() == DataType.OBJECT) {
					DataObject fieldDataObject = new DataObject();
					fieldDataObject.setFields(field.getFields());
					fieldDataObject.setName(getFieldClassName(field));
					processDataObject(fieldDataObject);
				}
				if (field.getType() == DataType.ENUM) {
					NamedEnumeration fieldEnumeration = new NamedEnumeration();
					fieldEnumeration.setValues(field.getValues());
					fieldEnumeration.setKey(field.getKey());
					fieldEnumeration.setName(getFieldClassName(field));
					processEnum(fieldEnumeration, mObjectPackage);
				}
				if (field.getType() == DataType.ARRAY && field.getItems().getType() == DataType.OBJECT) {
					DataObject fieldDataObject = new DataObject();
					fieldDataObject.setFields(field.getItems().getFields());
					fieldDataObject.setName(getFieldClassName(field));
					processDataObject(fieldDataObject);
				}
			}

			ClassName dtoClassName = ClassName.get(mObjectPackage, dataObject.getName());

			TypeSpec.Builder builder = TypeSpec.classBuilder(dtoClassName).addModifiers(Modifier.PUBLIC);

			ClassName futureProofEnumContainer = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE, "FutureProofEnumContainer");
			AnnotationSpec nullableAnnotation = AnnotationSpec.builder(Nullable.class).build();

			for (Field field : dataObject.getFields()) {
				try {
					FieldSpec fieldSpec = createFieldSpec(field, futureProofEnumContainer, nullableAnnotation);
					builder.addField(fieldSpec);
				} catch (Exception e) {
					throw new IllegalStateException(String.format("Error processing field %s in %s",
					                                              field.getLongName(),
					                                              dataObject.getName()), e);
				}
			}

			ClassName booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil");
			ClassName futureProofEnumAccessor = ClassName.get(FutureProofEnumAccessor.class);

			for (Field field : dataObject.getFields()) {
				DataType type = getEffectiveFieldType(field);
				if (type == DataType.ENUM) {
					TypeName typeName = getTypeName(field, field.isRequired(), true, false);
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.getLongName());

					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
							"get" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
					                                             .returns(typeName)
					                                             .addModifiers(Modifier.PUBLIC);
					getterBuilder.addStatement("return $N.getEnumValue()", field.getLongName());
					if (!field.isRequired() && field.getDefaultValue() == null) {
						getterBuilder.addAnnotation(nullableAnnotation);
						setterParameter.addAnnotation(nullableAnnotation);
					}
					builder.addMethod(getterBuilder.build());

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());
					setterBuilder.addStatement("this.$N.setEnumValue($N)", field.getLongName(), field.getLongName());
					builder.addMethod(setterBuilder.build());

					getterBuilder = MethodSpec.methodBuilder(
							"getFutureProof" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
					                          .returns(ParameterizedTypeName.get(futureProofEnumAccessor, typeName))
					                          .addModifiers(Modifier.PUBLIC);
					getterBuilder.addStatement("return $N.asReadOnly()", field.getLongName());
					builder.addMethod(getterBuilder.build());
				} else {
					TypeName typeName = getTypeName(field, field.isRequired(), true);
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.getLongName());

					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
							"get" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
					                                             .returns(typeName)
					                                             .addModifiers(Modifier.PUBLIC);

					boolean convertToBoolean = field.getType() == DataType.INT && hasBooleanRestriction(field);
					if (convertToBoolean) {
						getterBuilder.addStatement("return $T.convertToBoolean($N)", booleanUtil, field.getLongName());
					} else {
						getterBuilder.addStatement("return $N", field.getLongName());
					}

					if (!field.isRequired() && field.getDefaultValue() == null) {
						getterBuilder.addAnnotation(nullableAnnotation);
						setterParameter.addAnnotation(nullableAnnotation);
					}

					builder.addMethod(getterBuilder.build());

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());

					if (convertToBoolean) {
						if (field.isRequired()) {
							setterBuilder.addStatement("this.$N = $T.convertToInt($N)",
							                           field.getLongName(),
							                           booleanUtil,
							                           field.getLongName());
						} else {
							setterBuilder.addStatement("this.$N = $T.convertToInteger($N)",
							                           field.getLongName(),
							                           booleanUtil,
							                           field.getLongName());
						}
					} else {
						setterBuilder.addStatement("this.$N = $N", field.getLongName(), field.getLongName());
					}

					builder.addMethod(setterBuilder.build());
				}
			}

//		builder.addMethod(MethodSpec.constructorBuilder()
//		                            .addModifiers(Modifier.PUBLIC)
//		                            .addParameter(int.class, "id")
//		                            .addStatement("$N = $N",
//				                            "this.id",
//				                            "id")
//		                            .build());
//
//		builder.addMethod(MethodSpec.methodBuilder("getId")
//		                            .returns(int.class)
//		                            .addModifiers(Modifier.PUBLIC)
//		                            .addStatement("return $N",
//				                            "id")
//		                            .build());

			writeFormattedClassFile(mObjectPackage, builder);
		}

		@NotNull
		private FieldSpec createFieldSpec(Field field,
		                                  ClassName futureProofEnumContainer,
		                                  AnnotationSpec nullableAnnotation)
		{
			FieldSpec.Builder fieldBuilder = FieldSpec.builder(getTypeName(field, field.isRequired()),
			                                                   field.getLongName())
			                                          .addModifiers(Modifier.PRIVATE)
			                                          .addAnnotation(AnnotationSpec.builder(SerializedName.class)
			                                                                       .addMember("value",
			                                                                                  "$S",
			                                                                                  field.getName())
			                                                                       .build());

			if (!field.isRequired() && field.getDefaultValue() == null) {
				fieldBuilder.addAnnotation(nullableAnnotation);
			}

			DataType type = getEffectiveFieldType(field);
			if (!field.isRequired() && field.getDefaultValue() != null) {
				switch (type) {
					case STRING:
						fieldBuilder.initializer("$S", field.getDefaultValue());
						break;
					case ENUM:
						ClassName className = ClassName.get(mObjectPackage,
						                                    getFieldClassName(field));
						fieldBuilder.initializer(CodeBlock.builder()
						                                  .addStatement("new $T<>($T.class)", futureProofEnumContainer,
						                                                className)
						                                  .build());
						fieldBuilder.addModifiers(Modifier.FINAL);
						break;
					default:
						fieldBuilder.initializer("$L", field.getDefaultValue());
						break;
				}
			} else if (type == DataType.ENUM) {
				ClassName className = ClassName.get(mObjectPackage, getFieldClassName(field));
				fieldBuilder.initializer("new $T<>($T.class)", futureProofEnumContainer, className);
				fieldBuilder.addModifiers(Modifier.FINAL);
			}
			FieldSpec fieldSpec = fieldBuilder.build();
			return fieldSpec;
		}

		private String getFieldClassName(Field field) {
			if (field.isTypeRef()) {
				return field.getTypeRef();
			}
			return mJavaTool.fieldToClassStyle(field);
		}

		private DataType getEffectiveFieldType(Field field) {
			if (field.isTypeRef()) {
				return mDocument.getTypeByName(field.getTypeRef()).getType();
			} else {
				return field.getType();
			}
		}

		private TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                             boolean required)
		{
			return getTypeName(typeSpec, required, false);
		}

		private TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                             boolean required,
		                             boolean convertIntBoolean)
		{
			return getTypeName(typeSpec, required, convertIntBoolean, true);
		}

		private TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                             boolean required,
		                             boolean convertIntBoolean,
		                             boolean futureProofEnum)
		{
			DataType type = typeSpec.getType();
			if (type != null) {
				switch (type) {
					case INT:
						if (convertIntBoolean && hasBooleanRestriction(typeSpec)) {
							return getBasicTypeName(DataType.BOOLEAN, required);
						}
						return getBasicTypeName(DataType.INT, required);
					case LONG:
					case FLOAT:
					case DOUBLE:
					case BOOLEAN:
						return getBasicTypeName(type, required);
					case OBJECT:
						if (typeSpec instanceof Field) {
							Field field = (Field) typeSpec;
							return ClassName.get(mObjectPackage, mJavaTool.fieldToClassStyle(field));
						} else {
							return ClassName.get(Object.class);
						}
					case STRING:
						if (hasBooleanRestriction(typeSpec)) {
							return getBasicTypeName(DataType.BOOLEAN, required);
						}
						return ClassName.get(String.class);
					case DATE:
						return ClassName.get(LocalDate.class);
					case COLLECTION:
						return ParameterizedTypeName.get(ClassName.get(Map.class),
						                                 getKeyTypeName(typeSpec.getKey()),
						                                 getTypeName(typeSpec.getItems(), false, convertIntBoolean));
					case ENUM:
						if (typeSpec instanceof Field) {
							Field field = (Field) typeSpec;
							ClassName enumName = ClassName.get(mObjectPackage, mJavaTool.fieldToClassStyle(field));
							if (futureProofEnum) {
								ClassName futureProofEnumContainer = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE,
								                                                   "FutureProofEnumContainer");
								return ParameterizedTypeName.get(futureProofEnumContainer, enumName);
							} else {
								return enumName;
							}
						} else {
							throw new IllegalStateException("Raw enum type specified, cannot generate name.");
						}
					case ARRAY:
						// pass required false, since we can't use primitives
						return ParameterizedTypeName.get(ClassName.get(List.class), getTypeName(typeSpec.getItems(), false, convertIntBoolean));
				}
			} else if (typeSpec.getTypeRef() != null) {
				NamedType namedType = mDocument.getTypeByName(typeSpec.getTypeRef());
				if (namedType instanceof DataObject) {
					return ClassName.get(mTypeRefPackage, typeSpec.getTypeRef());
				} else if (namedType instanceof NamedEnumeration) {
					ClassName enumName = ClassName.get(mTypeRefPackage, typeSpec.getTypeRef());
					if (futureProofEnum) {
						ClassName futureProofEnumContainer = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE,
						                                                   "FutureProofEnumContainer");
						return ParameterizedTypeName.get(futureProofEnumContainer, enumName);
					} else {
						return enumName;
					}
				} else {
					throw new UnsupportedOperationException(
							"Unsupported named type class: " + namedType.getClass().getSimpleName() + " for " + typeSpec.getTypeRef() + ".");
				}
			}
			throw new IllegalStateException("No type or type reference");
		}

		private TypeName getBasicTypeName(DataType type, boolean required) {
			TypeName typeName = getBasicTypeName(type);
			if (!required) {
				typeName = typeName.box();
			}
			return typeName;
		}

		private TypeName getBasicTypeName(DataType type) {
			switch (type) {
				case INT:
					return TypeName.INT;
				case LONG:
					return TypeName.LONG;
				case FLOAT:
					return TypeName.FLOAT;
				case DOUBLE:
					return TypeName.DOUBLE;
				case BOOLEAN:
					return TypeName.BOOLEAN;
				default:
					throw new IllegalArgumentException("Unsupported type");
			}
		}

		private TypeName getKeyTypeName(KeyType key) {
			switch (key.getType()) {
				case INT:
					return ClassName.get(Integer.class);
				case LONG:
					return ClassName.get(Long.class);
				case STRING:
					return ClassName.get(String.class);
				default:
					throw new IllegalArgumentException("Unsupported key type");
			}
		}

		private boolean hasBooleanRestriction(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec) {
			return mJavaTool.hasBooleanRestriction(typeSpec);
		}
	}
}
