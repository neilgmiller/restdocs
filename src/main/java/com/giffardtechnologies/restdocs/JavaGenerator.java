package com.giffardtechnologies.restdocs;

import com.allego.util.futureproofenum.FutureProof;
import com.allego.util.futureproofenum.FutureProofEnumAccessor;
import com.allego.util.futureproofenum.FutureProofEnumContainer;
import com.allego.util.futureproofenum.IntId;
import com.allego.util.futureproofenum.LongId;
import com.allego.util.futureproofenum.StringId;
import com.allego.util.futureproofenum.Unknown;
import com.giffardtechnologies.restdocs.codegen.JavaDataObject;
import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.FieldReference;
import com.giffardtechnologies.restdocs.domain.Method;
import com.giffardtechnologies.restdocs.domain.NamedEnumeration;
import com.giffardtechnologies.restdocs.domain.Response;
import com.giffardtechnologies.restdocs.domain.type.BasicType;
import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.EnumConstant;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.KeyType;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.giffardtechnologies.restdocs.gson.GsonFactory;
import com.giffardtechnologies.restdocs.mappers.JavaFieldMapper;
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
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("CommentedOutCode")
@Command(description = "Generates java code based for a given API descriptor", name = "java_generator", mixinStandardHelpOptions = true, version = "1.0")
public class JavaGenerator implements Callable<Void> {
	private static final Logger sLogger = LoggerFactory.getLogger(JavaGenerator.class);

	private static final String FUTURE_PROOF_ENUM_PACKAGE = "com.allego.util.futureproofenum";
	public static final ClassName CLASS_NAME_FUTURE_PROOF_ENUM_CONTAINER = ClassName.get(FutureProofEnumContainer.class);
	public static final ClassName CLASS_NAME_BOOLEAN_UTIL = ClassName.get("com.allego.api.client2.helpers",
	                                                                      "BooleanUtil");
	public static final ClassName CLASS_NAME_FUTURE_PROOF_ENUM_ACCESSOR = ClassName.get(FutureProofEnumAccessor.class);

	public static final AnnotationSpec NULLABLE_ANNOTATION = AnnotationSpec.builder(Nullable.class).build();

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

	private boolean mUsePathAnnotation;
	private String mMethodIDStyleRequestClass;
	private String mAuthenticatedMethodIDStyleRequestClass;
	private String mPathAndBodyStyleRequestClass;

	public static void main(String[] args) {
//		for (String arg : args) {
//			System.out.print(arg);
//			System.out.print(", ");
//		}
//		System.out.println();
		JavaGenerator docGenerator = new JavaGenerator();

		new CommandLine(docGenerator).execute(args);
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

		String basename = mPropertiesFile.getName().replaceAll(".properties$", "");
		File localPropertiesFile = new File(mPropertiesFile.getParentFile(), basename + "-local.properties");
		Properties localProperties = new Properties(mProperties);
		System.out.println("Looking for " + localPropertiesFile);
		if (localPropertiesFile.exists()) {
			propsInStream = new BufferedInputStream(new FileInputStream(localPropertiesFile));
			localProperties.load(propsInStream);
			propsInStream.close();
		}

		mUsePathAnnotation = Boolean.parseBoolean(mProperties.getProperty("usePathAnnotation"));
		mMethodIDStyleRequestClass = mProperties.getProperty("altMethodIDStyleRequestClass", "AllegoRequest");
		mAuthenticatedMethodIDStyleRequestClass = mProperties.getProperty("altAuthenticatedMethodIDStyleRequestClass",
		                                                                  "AuthenticatedAllegoRequest");
		mPathAndBodyStyleRequestClass = mProperties.getProperty("altPathAndBodyStyleRequestClass");

		setSourceFile(new File(mPropertiesFile.getParentFile(), localProperties.getProperty("sourceFile")));

		generateCode();

		return null;
	}

	public void setSourceFile(File sourceFile) {
		mSourceFile = sourceFile;
	}

	private void generateCode() throws IOException {
		mDocument = parseDocument();
		mJavaTool = new JavaTool(mDocument);

		mCodeDir = new File(mPropertiesFile.getParentFile(), mProperties.getProperty("codeDir"));
		// TODO check this exists

		String dtoPackage = mProperties.getProperty("dtoPackage");

		initCodeFormatter();

		List<DataObject> dataObjects;
		if (mProperties.containsKey("javagen.includeDataObjects")) {
			Set<String> includeDataObjects = getSetProperty("javagen.includeDataObjects", s -> s);
			dataObjects = mDocument.getDataObjects()
			                       .stream()
			                       .filter(dataObject -> includeDataObjects.contains(dataObject.name))
			                       .collect(Collectors.toList());
		} else {
			dataObjects = mDocument.getDataObjects();
		}
		Set<FieldReference> forceTopLevel;
		if (mProperties.containsKey("javagen.forceTopLevel")) {
			forceTopLevel = getSetProperty("javagen.forceTopLevel", FieldReference::fromString);
		} else {
			forceTopLevel = Collections.emptySet();
		}

		if (mProperties.containsKey("javagen.excludeFields")) {
			Set<FieldReference> excludeFields = getSetProperty("javagen.excludeFields", FieldReference::fromString);
			for (DataObject dataObject : dataObjects) {
				removeExcludedFields(JavaDataObject.fromDataObject(dataObject), excludeFields);
			}
		}

		DataObjectProcessor dataObjectProcessor = new DataObjectProcessor(dtoPackage, dtoPackage, forceTopLevel);

		for (DataObject dataObject : dataObjects) {
			dataObjectProcessor.processDataObject(dataObject);
		}

		List<NamedEnumeration> enumerations;
		if (mProperties.containsKey("javagen.includeEnums")) {
			Set<String> includeDataObjects = getSetProperty("javagen.includeEnums", s -> s);
			enumerations = mDocument.enumerations
			                        .stream()
			                        .filter(namedEnumeration -> includeDataObjects.contains(namedEnumeration.name))
			                        .collect(Collectors.toList());
		} else {
			enumerations = mDocument.enumerations;
		}
		for (NamedEnumeration namedEnumeration : enumerations) {
			processEnum(namedEnumeration, dtoPackage);
		}

		if (mDocument.service != null) {
			// create the requests & responses
			String responsePackage = mProperties.getProperty("responsePackage");
			String requestPackage = mProperties.getProperty("requestPackage");

			List<DataObject> responseObjects;
			if (mProperties.containsKey("javagen.includeResponseObjects")) {
				Set<String> includeDataObjects = getSetProperty("javagen.includeResponseObjects", s -> s);
				responseObjects = mDocument.service.common.responseDataObjects
				                           .stream()
				                           .filter(dataObject -> includeDataObjects.contains(dataObject.name))
				                           .collect(Collectors.toList());
			} else {
				responseObjects = mDocument.service.common.responseDataObjects;
			}

			dataObjectProcessor = new DataObjectProcessor(responsePackage, dtoPackage, forceTopLevel);

			for (DataObject dataObject : responseObjects) {
				dataObjectProcessor.processDataObject(dataObject);
			}

			List<Method> methods;
			if (mProperties.containsKey("javagen.includeMethodIDs")) {
				Set<Integer> includeDataObjects = getSetProperty("javagen.includeMethodIDs", Integer::parseInt);
				methods = mDocument.service
				                   .getMethods()
				                   .stream()
				                   .filter(method -> includeDataObjects.contains(method.id))
				                   .collect(Collectors.toList());
			} else {
				methods = mDocument.service.getMethods();
			}

			MethodProcessor methodProcessor = new MethodProcessor(requestPackage,
			                                                      requestPackage + ".requestdata",
			                                                      responsePackage,
			                                                      dtoPackage);

			for (Method method : methods) {
				methodProcessor.processMethod(method);
			}
		}
	}

	private void removeExcludedFields(JavaDataObject dataObject, Set<FieldReference> excludedFields) {
		Set<FieldReference> scopedForceTopLevel = excludedFields.stream()
		                                                        .filter(fieldReference -> fieldReference.isNode(
				                                                        dataObject.getName()))
		                                                        .filter(Predicate.not(FieldReference::isLeafNode))
		                                                        .map(FieldReference::getChild)
		                                                        .collect(Collectors.toSet());
		Set<String> thisForceTopLevel = scopedForceTopLevel.stream()
		                                                   .filter(FieldReference::isLeafNode)
		                                                   .map(FieldReference::getNode)
		                                                   .collect(Collectors.toSet());

		ArrayList<Field> fields = dataObject.getFields()
		                                    .stream()
		                                    .filter(field -> !thisForceTopLevel.contains(field.longName))
		                                    .collect(Collectors.toCollection(ArrayList::new));
		dataObject.setFields(fields);

		for (Field field : fields) {
			if (field.type == DataType.OBJECT) {
				removeExcludedFields(JavaDataObject.fromField(field), scopedForceTopLevel);
			}
		}
	}

	@NotNull
	private <T> Set<T> getSetProperty(String key, Function<String, T> parser) {
		String property = mProperties.getProperty(key);
		Set<T> includeDataObjects;
		if (property.isBlank()) {
			includeDataObjects = Collections.emptySet();
		} else {
			includeDataObjects = Arrays.stream(property.split(",")).map(parser).collect(Collectors.toSet());
		}
		return includeDataObjects;
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

		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_JAVADOC_COMMENT,
		            DefaultCodeFormatterConstants.TRUE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_BLOCK_COMMENT,
		            DefaultCodeFormatterConstants.TRUE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_LINE_LENGTH, 40);
		options.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_CLEAR_BLANK_LINES_IN_JAVADOC_COMMENT,
		            DefaultCodeFormatterConstants.TRUE);

		options.put(DefaultCodeFormatterConstants.FORMATTER_KEEP_ANNOTATION_DECLARATION_ON_ONE_LINE,
		            DefaultCodeFormatterConstants.ONE_LINE_ALWAYS);

		options.put(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION,
		            DefaultCodeFormatterConstants.NEXT_LINE_ON_WRAP);
//		options.put(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_CONSTRUCTOR_DECLARATION, DefaultCodeFormatterConstants.NEXT_LINE);

		// instantiate the default code formatter with the given options
		mCodeFormatter = ToolFactory.createCodeFormatter(options);
	}

	private Document parseDocument() throws IOException {
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(mSourceFile));
		Yaml yaml = new Yaml();
		Map<String, Object> map = yaml.load(input);
		input.close();

		GsonBuilder gsonBuilder = GsonFactory.getGsonBuilder();

		Gson gson = gsonBuilder.create();
		String json = gson.toJson(map);
		Document document = gson.fromJson(json, Document.class);

		document.buildMappings();

		return document;
	}

	private void processEnum(NamedEnumeration namedEnumeration, String enumPackage) {
		processEnum(namedEnumeration, enumPackage, true);
	}

	/**
	 * @param namedEnumeration   the enumeration to process
	 * @param enumPackage        the package where teh enum should be placed
	 * @param useFutureProofEnum whether the enum should be generating using {@link FutureProof} annotations
	 */
	private void processEnum(NamedEnumeration namedEnumeration, String enumPackage, @SuppressWarnings("SameParameterValue") boolean useFutureProofEnum) {
		TypeSpec typeSpec = processEnumToTypeSpec(namedEnumeration, enumPackage, useFutureProofEnum);

		writeFormattedClassFile(enumPackage, typeSpec);
	}

	@NotNull
	private TypeSpec processEnumToTypeSpec(NamedEnumeration namedEnumeration,
	                                       String enumPackage,
	                                       boolean useFutureProofEnum)
	{
		ClassName enumClassName = ClassName.get(enumPackage, namedEnumeration.name);

		return processEnumToTypeSpec(namedEnumeration, enumClassName, useFutureProofEnum);
	}

	@NotNull
	private TypeSpec processEnumToTypeSpec(NamedEnumeration namedEnumeration,
	                                       ClassName enumClassName,
	                                       boolean useFutureProofEnum)
	{
		TypeSpec.Builder builder = TypeSpec.enumBuilder(enumClassName).addModifiers(Modifier.PUBLIC);

		ClassName futureProofAnnotation = ClassName.get(FutureProof.class);
		ClassName futureProofUnknownAnnotation = ClassName.get(Unknown.class);

		Class<?> enumKeyTypeClass = null;
		switch (namedEnumeration.key) {
			case INT:
				enumKeyTypeClass = int.class;
				builder.addSuperinterface(ClassName.get(IntId.class));
				if (useFutureProofEnum) {
					builder.addAnnotation(futureProofAnnotation);
					builder.addEnumConstant("UNKNOWN",
					                        TypeSpec.anonymousClassBuilder("$L", -1)
					                                .addAnnotation(futureProofUnknownAnnotation)
					                                .build());
				}
				break;
			case LONG:
				enumKeyTypeClass = long.class;
				builder.addSuperinterface(ClassName.get(LongId.class));
				break;
			case STRING:
				enumKeyTypeClass = String.class;
				builder.addSuperinterface(ClassName.get(StringId.class));
				break;
			case ENUM:
				throw new IllegalStateException(String.format("Cannot use enum as key to an enum: %s", enumClassName));
		}
		Objects.requireNonNull(enumKeyTypeClass, "Error in JavaGenerator enumKeyTypeClass is null");

		for (EnumConstant enumConstant : namedEnumeration.values) {
			String enumConstantValue = enumConstant.value;
			if (namedEnumeration.key == KeyType.STRING) {
				enumConstantValue = "\"" + enumConstantValue + "\"";
			}
			builder.addEnumConstant(convertToEnumConstantStyle(enumConstant.getLongName()),
			                        TypeSpec.anonymousClassBuilder("$L", enumConstantValue).build());
		}

		FieldSpec.Builder idFieldBuilder = FieldSpec.builder(enumKeyTypeClass, "id")
		                                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL);
		builder.addField(idFieldBuilder.build());

		builder.addMethod(MethodSpec.constructorBuilder()
		                            .addParameter(enumKeyTypeClass, "id")
		                            .addStatement("$N = $N", "this.id", "id")
		                            .build());

		builder.addMethod(MethodSpec.methodBuilder("getId")
		                            .returns(enumKeyTypeClass)
		                            .addModifiers(Modifier.PUBLIC)
		                            .addStatement("return $N", "id")
		                            .build());

		return builder.build();
	}

	@NotNull
	private String convertToEnumConstantStyle(String longName) {
		if (longName.matches("_-")) {
			return longName.toUpperCase().replace('-', '_');
		} else {
			return mJavaTool.toConstantStyle(longName);
		}
	}

	private void writeFormattedClassFile(String packageName, TypeSpec.Builder builder) {
		writeFormattedClassFile(packageName, builder.build());
	}

	private void writeFormattedClassFile(String packageName, TypeSpec typeSpec) {
		try {
			StringBuilder source = new StringBuilder();
			JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
			                            .indent("    ")
			                            .skipJavaLangImports(true) // NOTE - this is a little dangerous if there is a naming collision
			                            .build();
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
			//noinspection ResultOfMethodCallIgnored
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

	@SuppressWarnings({"SameParameterValue", "unused", "UnnecessaryLocalVariable"})
	private class FieldAndTypeProcessor {

		protected final String mObjectPackage;
		@Nullable
		protected final String mSubObjectPackage;
		protected final String mTypeRefPackage;

		private FieldAndTypeProcessor(String objectPackage, String typeRefPackage) {
			this(objectPackage, null, typeRefPackage);
		}

		public FieldAndTypeProcessor(String objectPackage, @Nullable String subObjectPackage, String typeRefPackage) {
			mObjectPackage = objectPackage;
			mSubObjectPackage = subObjectPackage;
			mTypeRefPackage = typeRefPackage;
		}

		protected String getSubObjectPackage() {
			return mSubObjectPackage == null ? mObjectPackage : mSubObjectPackage;
		}

		@NotNull
		protected FieldSpec createFieldSpec(Field field, AnnotationSpec nullableAnnotation)
		{
			return createFieldSpec(field, nullableAnnotation, true);
		}

		@NotNull
		protected FieldSpec createFieldSpec(Field field,
		                                    AnnotationSpec nullableAnnotation,
		                                    @SuppressWarnings("SameParameterValue") @Nullable ClassName objectClassName)
		{
			return createFieldSpec(field, nullableAnnotation, true, objectClassName, true);
		}

		@NotNull
		protected FieldSpec createFieldSpec(Field field,
		                                    AnnotationSpec nullableAnnotation,
		                                    @SuppressWarnings("SameParameterValue") boolean useFutureProofEnum)
		{
			return createFieldSpec(field, nullableAnnotation, useFutureProofEnum, null);
		}

		@NotNull
		protected FieldSpec createFieldSpec(Field field,
		                                    AnnotationSpec nullableAnnotation,
		                                    boolean useFutureProofEnum,
		                                    @SuppressWarnings("SameParameterValue") @Nullable ClassName objectClassName)
		{
			return createFieldSpec(field, nullableAnnotation, useFutureProofEnum, objectClassName, true);
		}

		@NotNull
		protected FieldSpec createFieldSpec(Field field,
		                                    AnnotationSpec nullableAnnotation,
		                                    boolean useFutureProofEnum,
		                                    @Nullable ClassName objectClassName,
		                                    boolean initializeCollections)
		{
			if (field instanceof JavaField) {
				JavaField javaField = (JavaField) field;
				if (objectClassName == null && javaField.getTypeName() instanceof ClassName) {
					objectClassName = (ClassName) javaField.getTypeName();
				}
			}
			boolean isNullable = !field.isRequired() && field.defaultValue == null;
			FieldSpec.Builder fieldBuilder = FieldSpec.builder(getTypeName(field,
			                                                               !isNullable,
			                                                               false,
			                                                               useFutureProofEnum,
			                                                               objectClassName), field.longName)
			                                          .addModifiers(Modifier.PRIVATE)
			                                          .addAnnotation(AnnotationSpec.builder(SerializedName.class)
			                                                                       .addMember("value",
			                                                                                  "$S", field.name)
			                                                                       .build());

			if (isNullable && !(field.type == DataType.ARRAY || field.type == DataType.COLLECTION)) {
				fieldBuilder.addAnnotation(nullableAnnotation);
			}

			// add initializers
			DataType type = getEffectiveFieldType(field);
			if (!field.isRequired() && (field.defaultValue != null ||
					(initializeCollections && (type == DataType.ARRAY || type == DataType.COLLECTION))))
			{
				switch (type) {
					case STRING:
						fieldBuilder.initializer("$S", field.defaultValue);
						break;
					case ENUM:
						ClassName className;
						if (field.isTypeRef()) {
							className = ClassName.get(mTypeRefPackage, getFieldClassName(field));
						} else if (objectClassName != null) {
							className = objectClassName;
						} else {
							className = ClassName.get(getSubObjectPackage(), getFieldClassName(field));
						}
						if (useFutureProofEnum) {
							fieldBuilder.initializer("new $T<>($T.class, $T.$L)",
							                         JavaGenerator.CLASS_NAME_FUTURE_PROOF_ENUM_CONTAINER,
							                         className,
							                         className,
							                         convertToEnumConstantStyle(field.defaultValue));
							fieldBuilder.addModifiers(Modifier.FINAL);
						} else {
							fieldBuilder.initializer("$T.$L", className, convertToEnumConstantStyle(field.defaultValue));
						}
						break;
					case ARRAY:
						fieldBuilder.initializer("$T.emptyList()", ClassName.get(Collections.class));
						break;
					case COLLECTION:
						fieldBuilder.initializer("$T.emptyMap()", ClassName.get(Collections.class));
						break;
					default:
						fieldBuilder.initializer("$L", field.defaultValue);
						break;
				}
			} else if (type == DataType.ENUM && useFutureProofEnum) {
				ClassName className;
				if (field.isTypeRef()) {
					className = ClassName.get(mTypeRefPackage, getFieldClassName(field));
				} else if (objectClassName != null) {
					className = objectClassName;
				} else {
					className = ClassName.get(getSubObjectPackage(), getFieldClassName(field));
				}
				fieldBuilder.initializer("new $T<>($T.class)",
				                         JavaGenerator.CLASS_NAME_FUTURE_PROOF_ENUM_CONTAINER,
				                         className);
				fieldBuilder.addModifiers(Modifier.FINAL);
			} else if (type == DataType.ARRAY || type == DataType.COLLECTION) {
				switch (type) {
					case ARRAY:
						fieldBuilder.initializer("$T.emptyList()", ClassName.get(Collections.class));
						break;
					case COLLECTION:
						fieldBuilder.initializer("$T.emptyMap()", ClassName.get(Collections.class));
						break;
					default:
						break;
				}
			}

			FieldSpec fieldSpec = fieldBuilder.build();
			return fieldSpec;
		}

		protected String getFieldClassName(Field field) {
			if (field.isTypeRef()) {
				return field.typeRef;
			}
			return mJavaTool.fieldToClassStyle(field);
		}

		protected String getFieldInnerClassName(Field field) {
			if (field.isTypeRef()) {
				throw new IllegalArgumentException("Passed fields cannot be a TypeRef");
			}
			// STOPSHIP: 2/10/21 figure out override for this variable
			return mJavaTool.fieldToClassStyle(field, true);
		}

		protected DataType getEffectiveFieldType(Field field) {
			if (field.isTypeRef()) {
				return mDocument.getTypeByName(field.typeRef).type;
			} else {
				return field.interpretedAs == null ? field.type : field.interpretedAs.asDataType();
			}
		}

		private TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec, boolean required)
		{
			return getTypeName(typeSpec, required, false);
		}

		protected TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                               boolean required,
		                               boolean convertIntBoolean)
		{
			return getTypeName(typeSpec, required, convertIntBoolean, true);
		}

		protected TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                               boolean required,
		                               boolean convertIntBoolean,
		                               boolean futureProofEnum)
		{
			return getTypeName(typeSpec, required, convertIntBoolean, futureProofEnum, null);
		}

		protected TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                               boolean required,
		                               boolean convertIntBoolean,
		                               @Nullable TypeName objectTypeName)
		{
			return getTypeName(typeSpec, required, convertIntBoolean, true, objectTypeName);
		}

		protected TypeName getTypeName(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec,
		                               boolean required,
		                               boolean convertIntBoolean,
		                               boolean futureProofEnum,
		                               @Nullable TypeName objectTypeName)
		{
			DataType type = typeSpec.type;
			if (type != null) {
				switch (type) {
					case INT:
						if (convertIntBoolean && interpretedAsBoolean(typeSpec)) {
							return getBasicTypeName(DataType.BOOLEAN, required);
						}
						return getBasicTypeName(DataType.INT, required);
					case LONG:
					case FLOAT:
					case DOUBLE:
					case BOOLEAN:
						return getBasicTypeName(type, required);
					case OBJECT:
						if (objectTypeName != null) {
							return objectTypeName;
						} else if (typeSpec instanceof Field) {
							Field field = (Field) typeSpec;
							return ClassName.get(getSubObjectPackage(), mJavaTool.fieldToClassStyle(field));
						} else {
							return ClassName.get(Object.class);
						}
					case STRING:
						if (interpretedAsBoolean(typeSpec)) {
							return getBasicTypeName(DataType.BOOLEAN, required);
						}
						return ClassName.get(String.class);
					case DATE:
						return ClassName.get(LocalDate.class);
					case COLLECTION:
						return ParameterizedTypeName.get(ClassName.get(Map.class),
						                                 getKeyTypeName(typeSpec.key),
						                                 getTypeName(typeSpec.items,
						                                             false,
						                                             convertIntBoolean,
						                                             futureProofEnum,
						                                             objectTypeName));
					case ENUM:
						TypeName enumName;
						if (objectTypeName != null) {
							enumName = objectTypeName;
						} else if (typeSpec instanceof Field) {
							Field field = (Field) typeSpec;
							enumName = ClassName.get(getSubObjectPackage(),
							                         mJavaTool.fieldToClassStyle(field));
						} else {
							throw new IllegalStateException("Raw enum type specified, cannot generate name.");
						}
						if (futureProofEnum) {
							ClassName futureProofEnumContainer = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE,
							                                                   "FutureProofEnumContainer");
							return ParameterizedTypeName.get(futureProofEnumContainer, enumName);
						} else {
							return enumName;
						}
					case ARRAY:
						// pass required false, since we can't use primitives
						return ParameterizedTypeName.get(ClassName.get(List.class),
						                                 getTypeName(typeSpec.items,
						                                             false,
						                                             convertIntBoolean,
						                                             futureProofEnum,
						                                             objectTypeName));
					case BITSET:
						TypeName bitsetName;
						if (objectTypeName != null) {
							bitsetName = objectTypeName;
						} else if (typeSpec instanceof Field) {
							Field field = (Field) typeSpec;
							bitsetName = ClassName.get(getSubObjectPackage(),
							                         mJavaTool.fieldToClassStyle(field));
						} else {
							throw new IllegalStateException("Raw bitflag type specified, cannot generate name.");
						}
						return getBasicTypeName(typeSpec.flagType.type, required);
				}
			} else if (typeSpec.typeRef != null) {
				NamedType namedType = mDocument.getTypeByName(typeSpec.typeRef);
				if (namedType instanceof DataObject) {
					return ClassName.get(mTypeRefPackage, typeSpec.typeRef);
				} else if (namedType instanceof NamedEnumeration) {
					ClassName enumName = ClassName.get(mTypeRefPackage, typeSpec.typeRef);
					if (futureProofEnum) {
						ClassName futureProofEnumContainer = ClassName.get(FUTURE_PROOF_ENUM_PACKAGE,
						                                                   "FutureProofEnumContainer");
						return ParameterizedTypeName.get(futureProofEnumContainer, enumName);
					} else {
						return enumName;
					}
				} else {
					throw new UnsupportedOperationException(
							"Unsupported named type class: " + namedType.getClass().getSimpleName() + " for " +
									typeSpec.typeRef + ".");
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
			switch (key.type) {
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

		protected boolean hasBooleanRestriction(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec) {
			return mJavaTool.hasBooleanRestriction(typeSpec);
		}


		protected boolean interpretedAsBoolean(com.giffardtechnologies.restdocs.domain.type.TypeSpec typeSpec) {
			return hasBooleanRestriction(typeSpec) || typeSpec.interpretedAs == BasicType.BOOLEAN;
		}

	}

	private class DataObjectProcessor extends FieldAndTypeProcessor {

		private final Set<FieldReference> mForceTopLevel;

		private class ProcessingContext {
			private final DataObject mDataObject;
			private final Set<FieldReference> mForceTopLevel;
			private DataObjectProcessor mSubObjectProcessor;
			private DataObjectProcessor mTopLevelSubObjectProcessor;

			private ProcessingContext(DataObject dataObject, Set<FieldReference> forceTopLevel) {
				mDataObject = dataObject;
				mForceTopLevel = forceTopLevel;
			}

			public DataObject getDataObject() {
				return mDataObject;
			}

			public DataObjectProcessor getSubObjectProcessor() {
				if (mSubObjectProcessor == null) {
					mSubObjectProcessor = new DataObjectProcessor(mObjectPackage + "." + mDataObject.name,
					                                              mTypeRefPackage,
					                                              mForceTopLevel);
				}
				return mSubObjectProcessor;
			}

			public DataObjectProcessor getTopLevelSubObjectProcessor() {
				if (mTopLevelSubObjectProcessor == null) {
					mTopLevelSubObjectProcessor = new DataObjectProcessor(mObjectPackage,
					                                              mTypeRefPackage,
					                                              mForceTopLevel);
				}
				return mTopLevelSubObjectProcessor;
			}

		}

		private DataObjectProcessor(String objectPackage, String typeRefPackage, Set<FieldReference> forceTopLevel) {
			super(objectPackage, typeRefPackage);
			mForceTopLevel = forceTopLevel;
		}

		public ClassName processDataObject(DataObject dataObject) {
			TypeSpec typeSpec = processDataObjectToTypeSpec(dataObject);
			writeFormattedClassFile(mObjectPackage, typeSpec);
			return ClassName.get(mObjectPackage, dataObject.name);
		}

		public TypeSpec processDataObjectToTypeSpec(DataObject dataObject) {
			TypeSpec.Builder dataObjectClassBuilder = processDataObjectToBuilder(dataObject);

			return dataObjectClassBuilder.build();
		}

		@NotNull
		private TypeSpec.Builder processDataObjectToBuilder(DataObject dataObject)
		{
			Set<FieldReference> scopedForceTopLevel = mForceTopLevel.stream()
			                                                        .filter(fieldReference -> fieldReference.isNode(
					                                                        dataObject.name))
			                                                        .filter(Predicate.not(FieldReference::isLeafNode))
			                                                        .map(FieldReference::getChild)
			                                                        .collect(Collectors.toSet());
			Set<String> thisForceTopLevel = scopedForceTopLevel.stream()
			                                                   .filter(FieldReference::isLeafNode)
			                                                   .map(FieldReference::getNode)
			                                                   .collect(Collectors.toSet());

			ProcessingContext processingContext = new ProcessingContext(dataObject, scopedForceTopLevel);

			ClassName dtoClassName = ClassName.get(mObjectPackage, dataObject.name);
			TypeSpec.Builder dataObjectClassBuilder = TypeSpec.classBuilder(dtoClassName).addModifiers(Modifier.PUBLIC);

			List<JavaField> javaFields = new ArrayList<>();
			dataObject.getFields().stream().map(JavaFieldMapper.INSTANCE::dtoToJavaModel).map(field -> {
				if (field.longName.equalsIgnoreCase(dataObject.name + "id")) {
					field.longName = "id";
				} else {
					field.longName = mJavaTool.toGetterStyle(field.longName);
				}

				boolean forceTopLevel = thisForceTopLevel.contains(field.longName);
				if (field.type == DataType.OBJECT) {
					generateSubObject(processingContext,
					                  dtoClassName,
					                  dataObjectClassBuilder,
					                  field,
					                  forceTopLevel,
					                  field.getFields());
				} else if (field.type == DataType.ENUM) {
					NamedEnumeration fieldEnumeration = new NamedEnumeration();
					fieldEnumeration.values = field.values;
					fieldEnumeration.key = field.key;

					if (forceTopLevel) {
						fieldEnumeration.name = getFieldClassName(field);
						processEnum(fieldEnumeration, mObjectPackage);
						field.setTypeName(ClassName.get(mObjectPackage, fieldEnumeration.name));
					} else {
						String fieldInnerClassName = getFieldInnerClassName(field);
						fieldEnumeration.name = fieldInnerClassName;
						ClassName enumClassName = dtoClassName.nestedClass(fieldInnerClassName);
						TypeSpec typeSpec = processEnumToTypeSpec(fieldEnumeration, enumClassName, true);
						dataObjectClassBuilder.addType(typeSpec);
						field.setTypeName(enumClassName);
					}
				} else if (field.type == DataType.ARRAY && field.items.type == DataType.OBJECT) {
					generateSubObject(processingContext,
					                  dtoClassName,
					                  dataObjectClassBuilder,
					                  field,
					                  forceTopLevel,
					                  field.items.getFields());
				}

				return field;
			}).collect(Collectors.toCollection(() -> javaFields));

			AnnotationSpec nullableAnnotation = AnnotationSpec.builder(Nullable.class).build();

			for (JavaField field : javaFields) {
				try {
					FieldSpec fieldSpec = createFieldSpec(field, nullableAnnotation);
					dataObjectClassBuilder.addField(fieldSpec);
				} catch (Exception e) {
					throw new IllegalStateException(String.format("Error processing field %s in %s",
					                                              field.longName,
					                                              dataObject.name), e);
				}
			}

			MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
			// add empty constructor
			dataObjectClassBuilder.addMethod(constructorBuilder.build());
			// add full constructor
			for (JavaField field : javaFields) {
				DataType type = getEffectiveFieldType(field);
				try {
					if (type == DataType.ENUM) {
						TypeName typeName = getTypeName(field, field.isRequired(), true, false, field.getTypeName());
						ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

						if (!field.isRequired() && field.defaultValue == null) {
							setterParameter.addAnnotation(nullableAnnotation);
						}
						constructorBuilder.addParameter(setterParameter.build());
						constructorBuilder.addStatement("this.$N.setEnumValue($N)", field.longName, field.longName);
					} else {
						TypeName typeName = getTypeName(field, field.isRequired(), true, field.getTypeName());
						ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

						boolean convertToBoolean = field.type == DataType.INT && interpretedAsBoolean(field);

						if (!field.isRequired() && field.defaultValue == null &&
								!(field.type == DataType.ARRAY || field.type == DataType.COLLECTION))
						{
							setterParameter.addAnnotation(nullableAnnotation);
						}

						constructorBuilder.addParameter(setterParameter.build());
						if (convertToBoolean) {
							if (field.isRequired()) {
								constructorBuilder.addStatement("this.$N = $T.convertToInt($N)", field.longName,
								                                CLASS_NAME_BOOLEAN_UTIL, field.longName);
							} else if (field.hasDefaultValue()) {
								constructorBuilder.addStatement("this.$N = $T.convertToInt($N, $L)",
								                                field.longName,
								                                CLASS_NAME_BOOLEAN_UTIL,
								                                field.longName,
								                                field.defaultValue);
							} else {
								constructorBuilder.addStatement("this.$N = $T.convertToInteger($N)", field.longName,
								                                CLASS_NAME_BOOLEAN_UTIL, field.longName);
							}
						} else {
							constructorBuilder.addStatement("this.$N = $N", field.longName, field.longName);
						}
					}
				} catch (Exception e) {
					throw new IllegalStateException(String.format("Error processing field %s in %s",
					                                              field.longName,
					                                              dataObject.name), e);
				}
			}
			dataObjectClassBuilder.addMethod(constructorBuilder.build());

			for (JavaField field : javaFields) {
				DataType type = getEffectiveFieldType(field);
				boolean isOptionalWithDefault = !field.isRequired() && !field.hasDefaultValue();
				if (type == DataType.ENUM) {
					TypeName typeName = getTypeName(field, field.isRequired(), true, false, field.getTypeName());
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
							"get" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .returns(typeName)
					                                             .addModifiers(Modifier.PUBLIC);
					getterBuilder.addStatement("return $N.getEnumValue()", field.longName);

					if (!field.isRequired() && field.defaultValue == null) {
						getterBuilder.addAnnotation(nullableAnnotation);
						setterParameter.addAnnotation(nullableAnnotation);
					}
					dataObjectClassBuilder.addMethod(getterBuilder.build());

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());
					setterBuilder.addStatement("this.$N.setEnumValue($N)", field.longName, field.longName);
					dataObjectClassBuilder.addMethod(setterBuilder.build());

					getterBuilder = MethodSpec.methodBuilder(
							"getFutureProof" + mJavaTool.fieldNameToClassStyle(field.longName))
					                          .returns(ParameterizedTypeName.get(CLASS_NAME_FUTURE_PROOF_ENUM_ACCESSOR,
					                                                             typeName))
					                          .addModifiers(Modifier.PUBLIC);
					getterBuilder.addStatement("return $N.asReadOnly()", field.longName);
					dataObjectClassBuilder.addMethod(getterBuilder.build());
				} else {
					boolean convertToBoolean = field.type == DataType.INT && interpretedAsBoolean(field);

					TypeName typeName = getTypeName(field, !isOptionalWithDefault, true, field.getTypeName());
					TypeName setterTypeName = getTypeName(field, field.isRequired(), true, field.getTypeName());
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(setterTypeName, field.longName);

					String getterPrefix;
					if (convertToBoolean || field.type == DataType.BOOLEAN) {
						if (field.longName.startsWith("is")) {
							getterPrefix = "";
						} else {
							getterPrefix = "is";
						}
					} else {
						getterPrefix = "get";
					}
					String getterMethodName = getterPrefix + mJavaTool.fieldNameToClassStyle(field.longName);
					getterMethodName = StringUtils.uncapitalize(getterMethodName);

					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(getterMethodName)
					                                             .returns(typeName)
					                                             .addModifiers(Modifier.PUBLIC);

					if (convertToBoolean) {
						getterBuilder.addStatement("return $T.convertToBoolean($N)",
						                           CLASS_NAME_BOOLEAN_UTIL, field.longName);
					} else {
						getterBuilder.addStatement("return $N", field.longName);
					}

					if (!field.isRequired() && (field.defaultValue == null || convertToBoolean) &&
							!(field.type == DataType.ARRAY || field.type == DataType.COLLECTION))
					{
						if (!field.hasDefaultValue()) {
							getterBuilder.addAnnotation(nullableAnnotation);
						}
						setterParameter.addAnnotation(nullableAnnotation);
					}

					dataObjectClassBuilder.addMethod(getterBuilder.build());

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());

					if (convertToBoolean) {
						if (field.isRequired()) {
							setterBuilder.addStatement("this.$N = $T.convertToInt($N)", field.longName,
							                           CLASS_NAME_BOOLEAN_UTIL, field.longName);
						} else if (field.hasDefaultValue()) {
							setterBuilder.addStatement("this.$N = $T.convertToInt($N, $L)", field.longName,
							                           CLASS_NAME_BOOLEAN_UTIL, field.longName, field.defaultValue);
						} else {
							setterBuilder.addStatement("this.$N = $T.convertToInteger($N)", field.longName,
							                           CLASS_NAME_BOOLEAN_UTIL, field.longName);
						}
					} else {
						setterBuilder.addStatement("this.$N = $N", field.longName, field.longName);
					}

					dataObjectClassBuilder.addMethod(setterBuilder.build());
				}
			}
			return dataObjectClassBuilder;
		}

		private void generateSubObject(ProcessingContext processingContext,
		                               ClassName dtoClassName,
		                               TypeSpec.Builder dataObjectClassBuilder,
		                               JavaField field,
		                               boolean forceTopLevel,
		                               ArrayList<Field> fields)
		{
			String fieldClassName;
			if (forceTopLevel) {
				fieldClassName = getFieldClassName(field);
				field.setTypeName(ClassName.get(mObjectPackage, fieldClassName));
			} else {
				fieldClassName = getFieldInnerClassName(field);
				field.setTypeName(ClassName.get(mObjectPackage, dtoClassName.simpleName(), fieldClassName));
			}

			DataObject fieldDataObject = new DataObject();
			fieldDataObject.setFields(fields);
			fieldDataObject.name = fieldClassName;

			if (forceTopLevel) {
				processingContext.getTopLevelSubObjectProcessor().processDataObject(fieldDataObject);
			} else {
				dataObjectClassBuilder.addType(processingContext.getSubObjectProcessor()
				                                                .processDataObjectToBuilder(fieldDataObject)
				                                                .addModifiers(Modifier.STATIC)
				                                                .build());
			}
		}

	}

	public class MethodProcessor extends FieldAndTypeProcessor {
		private final String mResponsePackage;
		private final ClassName mAuthenticatedAllegoRequestClassName;
		private final ClassName mAllegoRequestClassName;
		private final ClassName mAllegoPathAndBodyRequestClassName;

		private MethodProcessor(String objectPackage,
		                        String paramsObjectPackage,
		                        String responsePackage,
		                        String typeRefPackage)
		{
			super(objectPackage, paramsObjectPackage, typeRefPackage);
			mResponsePackage = responsePackage;
			mAuthenticatedAllegoRequestClassName = ClassName.get(mObjectPackage + ".support",
			                                                     mAuthenticatedMethodIDStyleRequestClass);
			mAllegoRequestClassName = ClassName.get(mObjectPackage + ".support",
			                                        mMethodIDStyleRequestClass);
			mAllegoPathAndBodyRequestClassName = ClassName.get(mObjectPackage + ".support",
			                                        mPathAndBodyStyleRequestClass);
		}

		private void processMethod(Method method) {
			DataObjectProcessor dataObjectProcessor = new DataObjectProcessor(mResponsePackage, mTypeRefPackage,
			                                                                  Collections.emptySet());
			String methodName = StringUtils.capitalize(method.name);

			String responseClassName = null;
			TypeName responseTypeName = null;
			Response response = method.response;
			if (response != null) {
				if (response.isTypeRef()) {
					responseClassName = response.typeRef;
					try {
						responseTypeName = getTypeName(response, true, false);
					} catch (Exception e) {
						// nothing it's just not found
					}
				} else if (response.type == DataType.OBJECT) {
					responseClassName = methodName + "Response";

					// setup a temp DataObject
					DataObject dataObject = new DataObject();
					dataObject.name = responseClassName;
                    dataObject.parent = response.parentDocument;
					dataObject.setFields(response.getFields());

					// TODO this need to handle imports for type refs
					dataObjectProcessor.processDataObject(dataObject);
				}
			}

			String requestClassNameStr = methodName + "Request";
			ClassName requestClassName = ClassName.get(mObjectPackage, requestClassNameStr);
			ClassName baseClassName;
			if (method.isAuthenticationRequired()) {
				baseClassName = mAuthenticatedAllegoRequestClassName;
			} else {
				if (mUsePathAnnotation && method.id == null) {
					baseClassName = mAllegoPathAndBodyRequestClassName;
				} else {
					baseClassName = mAllegoRequestClassName;
				}
			}

			ClassName methodIDAnnotation = ClassName.get("com.allego.api.client2.requests.support", "MethodID");
			ClassName paramsClassName = method.getParameters().isEmpty() ? ClassName.get(Void.class) : ClassName.get(
					mObjectPackage + "." + requestClassNameStr,
					"Params");
			if (responseTypeName == null) {
				responseTypeName = responseClassName == null ? TypeName.get(Void.class) : ClassName.get(mResponsePackage,
				                                                                                        responseClassName);
			}
			ParameterizedTypeName superClassParamed = ParameterizedTypeName.get(baseClassName,
			                                                                    paramsClassName,
			                                                                    responseTypeName);

			TypeSpec.Builder requestClassBuilder = TypeSpec.classBuilder(requestClassName)
			                                               .superclass(superClassParamed)
			                                               .addModifiers(Modifier.PUBLIC);

			if (mUsePathAnnotation) {
				if (method.id != null) {
					AnnotationSpec methodAnnotation = AnnotationSpec.builder(methodIDAnnotation)
					                                                .addMember("id", "$L", method.id)
					                                                .build();
					requestClassBuilder.addAnnotation(methodAnnotation);
				}

				ClassName pathAnnotationCN = ClassName.get("javax.ws.rs", "Path");
				AnnotationSpec pathAnnotation = AnnotationSpec.builder(pathAnnotationCN)
				                                              .addMember("value", "\"/$L\"", method.path)
				                                              .build();
				requestClassBuilder.addAnnotation(pathAnnotation);
			} else {
				Objects.requireNonNull(method.id, "Method must have an ID");
				AnnotationSpec methodAnnotation = AnnotationSpec.builder(methodIDAnnotation)
				                                                .addMember("id", "$L", method.id)
				                                                .build();
				requestClassBuilder.addAnnotation(methodAnnotation);
			}

			if (method.getParameters().isEmpty()) {
				addMinimalConstructor(method, requestClassName, requestClassBuilder);
			} else {
				if (method.getParameters().size() < 3) {
					addMinimalConstructor(method, requestClassName, requestClassBuilder);
				}

				// make the constructor that the builder will use
				MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE);
				if (method.isAuthenticationRequired()) {
					constructorBuilder.addParameter(String.class, "accessKey");
				}
				constructorBuilder.addParameter(paramsClassName, "params");

				if (method.isAuthenticationRequired()) {
					constructorBuilder.addStatement("super($N, $N)", "accessKey", "params");
				} else {
					constructorBuilder.addStatement("super($N)", "params");
				}

				requestClassBuilder.addMethod(constructorBuilder.build());

				// prepare the params data object
				String paramsObjectPackage = mObjectPackage + "." + requestClassNameStr;
				DataObjectProcessor paramsDataObjectProcessor = new DataObjectProcessor(paramsObjectPackage,
				                                                                        mTypeRefPackage,
				                                                                        Collections.emptySet());

				for (Field field : method.getParameters()) {
					if (field.longName.equalsIgnoreCase(method.name + "id")) {
						field.longName = "id";
					}
				}

				// build the request builder inner class
				ClassName builderClassName = ClassName.get(mObjectPackage + "." + requestClassNameStr, "Builder");

				MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder("builder")
				                                             .returns(builderClassName)
				                                             .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

				getterBuilder.addStatement("return new $T()", builderClassName);
				requestClassBuilder.addMethod(getterBuilder.build());

				ClassName requestBuilderBaseClassName;
				if (method.isAuthenticationRequired()) {
					requestBuilderBaseClassName = ClassName.get(
							mAuthenticatedAllegoRequestClassName.canonicalName(),
							"AuthenticatedRequestBuilder");
				} else {
					if (mUsePathAnnotation) {
						requestBuilderBaseClassName = ClassName.get(mAllegoPathAndBodyRequestClassName.canonicalName(),
						                                            "RequestBuilder");
					} else {
						requestBuilderBaseClassName = ClassName.get(mAllegoRequestClassName.canonicalName(),
						                                            "RequestBuilder");
					}
				}
				ParameterizedTypeName builderSuperClass = ParameterizedTypeName.get(requestBuilderBaseClassName,
				                                                                    requestClassName,
				                                                                    builderClassName,
				                                                                    paramsClassName);

				TypeSpec.Builder builderClassBuilder = TypeSpec.classBuilder(builderClassName)
				                                               .superclass(builderSuperClass)
				                                               .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

				processParamsForBuilder(method, requestClassName, builderClassName, builderClassBuilder, false);

				requestClassBuilder.addType(builderClassBuilder.build());

				// build the Params inner class
				ClassName copyableClassName = ClassName.get("com.allego.api.client2.requests.support", "Copyable");
				ParameterizedTypeName paramsSuperClass = ParameterizedTypeName.get(copyableClassName, paramsClassName);

				TypeSpec.Builder paramsBuilder = TypeSpec.classBuilder(paramsClassName)
				                                         .superclass(paramsSuperClass)
				                                         .addSuperinterface(Cloneable.class)
				                                         .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

				processParams(method, requestClassName, paramsBuilder);

				requestClassBuilder.addType(paramsBuilder.build());

				for (Field field : method.getParameters()) {
					TypeSpec typeSpec = null;
					if (field.type == DataType.OBJECT) {
						DataObject fieldDataObject = new DataObject();
						fieldDataObject.setFields(field.getFields());
						fieldDataObject.name = getFieldClassName(field);
						typeSpec = paramsDataObjectProcessor.processDataObjectToBuilder(fieldDataObject)
						                                    .addModifiers(Modifier.STATIC)
						                                    .build();
					}
					if (field.type == DataType.ENUM) {
						NamedEnumeration fieldEnumeration = new NamedEnumeration();
						fieldEnumeration.values = field.values;
						fieldEnumeration.key = field.key;
						fieldEnumeration.name = getFieldClassName(field);
						typeSpec = processEnumToTypeSpec(fieldEnumeration, paramsObjectPackage, false);
					}
					if (field.type == DataType.ARRAY && field.items.type == DataType.OBJECT) {
						DataObject fieldDataObject = new DataObject();
						fieldDataObject.setFields(field.items.getFields());
						fieldDataObject.name = getFieldClassName(field);
						typeSpec = paramsDataObjectProcessor.processDataObjectToBuilder(fieldDataObject)
						                                    .addModifiers(Modifier.STATIC)
						                                    .build();
					}
					if (typeSpec != null) {
						requestClassBuilder.addType(typeSpec);
					}
				}
			}

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

			writeFormattedClassFile(mObjectPackage, requestClassBuilder);

		}

		private void addMinimalConstructor(Method method, ClassName requestClassName, TypeSpec.Builder requestClassBuilder) {
			MethodSpec.Builder builder = MethodSpec.constructorBuilder()
			                                       .addModifiers(Modifier.PUBLIC);

			String description = StringUtils.uncapitalize(method.description);

//			if (method.getParameters().isEmpty()) {
				if (method.isAuthenticationRequired()) {
					builder.addParameter(String.class, "accessKey")
					       .addStatement("super($N, null)", "accessKey")
					       .addJavadoc(CodeBlock.builder()
					                            .add("Creates a request that " + description)
					                            .add(" @param accessKey the access key to use for the request")
					                            .build());
				} else {
					builder.addStatement("super(null)")
					       .addJavadoc(CodeBlock.builder().add("Creates a request that " + description).build());

				}
//			} else {
//				if (method.isAuthenticationRequired()) {
//					builder.addParameter(String.class, "accessKey")
//					       .addStatement("super($N, null)", "accessKey")
//					       .addJavadoc(CodeBlock.builder()
//					                            .add("Creates a request that " + description)
//					                            .add(" @param accessKey the access key to use for the request")
//					                            .build());
//				} else {
//					builder.addStatement("super(null)")
//					       .addJavadoc(CodeBlock.builder().add("Creates a request that " + description).build());
//
//				}
//				processParamsForConstructor(method, requestClassName, builder);
//			}

			requestClassBuilder.addMethod(builder.build());
		}

		private void processParamsForBuilder(Method method, ClassName requestClassName, ClassName builderClassName,
		                                     TypeSpec.Builder builderClassBuilder,
		                                     boolean useFutureProofEnum)
		{
			ClassName booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil");
			ClassName futureProofEnumAccessor = ClassName.get(FutureProofEnumAccessor.class);

			for (Field field : method.getParameters()) {
				ClassName className = null;
				if (field.type == DataType.OBJECT) {
					className = ClassName.get(mObjectPackage, requestClassName.simpleName(), getFieldClassName(field));
				} else if (field.type == DataType.ENUM) {
					className = ClassName.get(mObjectPackage, requestClassName.simpleName(), getFieldClassName(field));
				} else if (field.type == DataType.ARRAY && field.items.type == DataType.OBJECT) {
					className = ClassName.get(mObjectPackage, requestClassName.simpleName(), getFieldClassName(field));
				}

				DataType type = getEffectiveFieldType(field);
				if (type == DataType.ENUM && useFutureProofEnum) {
					TypeName typeName = getTypeName(field, field.isRequired(), true, false);
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

					if (!field.isRequired() && field.defaultValue == null) {
						setterParameter.addAnnotation(NULLABLE_ANNOTATION);
					}

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());
					setterBuilder.addStatement("this.$N.setEnumValue($N)", field.longName, field.longName);
					setterBuilder.addStatement("return this;");
					builderClassBuilder.addMethod(setterBuilder.build());
				} else {
					TypeName typeName = getTypeName(field, field.isRequired(), true, useFutureProofEnum, className);
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

					boolean convertToBoolean = field.type == DataType.INT && interpretedAsBoolean(field);

					if (!field.isRequired() && field.defaultValue == null) {
						setterParameter.addAnnotation(NULLABLE_ANNOTATION);
					}

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(StringUtils.uncapitalize(mJavaTool.fieldNameToClassStyle(
							                                             field.longName)))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .returns(builderClassName)
					                                             .addParameter(setterParameter.build());

					if (convertToBoolean) {
						if (field.isRequired()) {
							setterBuilder.addStatement("this.params.$N = $T.convertToInt($N)", field.longName,
							                           booleanUtil, field.longName);
						} else {
							setterBuilder.addStatement("this.params.$N = $T.convertToInteger($N)", field.longName,
							                           booleanUtil, field.longName);
						}
					} else {
						setterBuilder.addStatement("this.params.$N = $N", field.longName, field.longName);
					}
					setterBuilder.addStatement("return this");

					builderClassBuilder.addMethod(setterBuilder.build());
				}
			}
		}

		private void processParams(Method method, ClassName requestClassName, TypeSpec.Builder paramsBuilder) {
			boolean useFutureProofEnum = false;

			ClassName booleanUtil = ClassName.get("com.allego.api.client2.helpers", "BooleanUtil");
			ClassName futureProofEnumAccessor = ClassName.get(FutureProofEnumAccessor.class);

			for (Field field : method.getParameters()) {
				ClassName className = null;
				if (field.type == DataType.OBJECT) {
					className = requestClassName.nestedClass(getFieldClassName(field));
				} else if (field.type == DataType.ENUM) {
					className = requestClassName.nestedClass(getFieldClassName(field));
				} else if (field.type == DataType.ARRAY && field.items.type == DataType.OBJECT) {
					className = requestClassName.nestedClass(getFieldClassName(field));
				}

				try {
					FieldSpec fieldSpec = createFieldSpec(field, NULLABLE_ANNOTATION, useFutureProofEnum, className, false);
					paramsBuilder.addField(fieldSpec);
				} catch (Exception e) {
					throw new IllegalStateException(String.format("Error processing field %s in %s",
					                                              field.longName,
					                                              method.name), e);
				}

				DataType type = getEffectiveFieldType(field);
				if (type == DataType.ENUM && useFutureProofEnum) {
					TypeName typeName = getTypeName(field, field.isRequired(), true, false, className);
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
							"get" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .returns(typeName)
					                                             .addModifiers(Modifier.PUBLIC);
					getterBuilder.addStatement("return $N.getEnumValue()", field.longName);
					if (!field.isRequired() && field.defaultValue == null) {
						getterBuilder.addAnnotation(NULLABLE_ANNOTATION);
						setterParameter.addAnnotation(NULLABLE_ANNOTATION);
					}
					paramsBuilder.addMethod(getterBuilder.build());

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());
					setterBuilder.addStatement("this.$N.setEnumValue($N)", field.longName, field.longName);
					paramsBuilder.addMethod(setterBuilder.build());

					getterBuilder = MethodSpec.methodBuilder(
							"getFutureProof" + mJavaTool.fieldNameToClassStyle(field.longName))
					                          .returns(ParameterizedTypeName.get(futureProofEnumAccessor, typeName))
					                          .addModifiers(Modifier.PUBLIC);
					getterBuilder.addStatement("return $N.asReadOnly()", field.longName);
					paramsBuilder.addMethod(getterBuilder.build());
				} else {
					TypeName typeName = getTypeName(field, field.isRequired(), true, useFutureProofEnum, className);
					ParameterSpec.Builder setterParameter = ParameterSpec.builder(typeName, field.longName);

					MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(
							"get" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .returns(typeName)
					                                             .addModifiers(Modifier.PUBLIC);

					boolean convertToBoolean = field.type == DataType.INT && interpretedAsBoolean(field);
					if (convertToBoolean) {
						getterBuilder.addStatement("return $T.convertToBoolean($N)", booleanUtil, field.longName);
					} else {
						getterBuilder.addStatement("return $N", field.longName);
					}

					if (!field.isRequired() && field.defaultValue == null) {
						getterBuilder.addAnnotation(NULLABLE_ANNOTATION);
						setterParameter.addAnnotation(NULLABLE_ANNOTATION);
					}

					paramsBuilder.addMethod(getterBuilder.build());

					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
							"set" + mJavaTool.fieldNameToClassStyle(field.longName))
					                                             .addModifiers(Modifier.PUBLIC)
					                                             .addParameter(setterParameter.build());

					if (convertToBoolean) {
						if (field.isRequired()) {
							setterBuilder.addStatement("this.$N = $T.convertToInt($N)", field.longName,
							                           booleanUtil, field.longName);
						} else {
							setterBuilder.addStatement("this.$N = $T.convertToInteger($N)", field.longName,
							                           booleanUtil, field.longName);
						}
					} else {
						setterBuilder.addStatement("this.$N = $N", field.longName, field.longName);
					}

					paramsBuilder.addMethod(setterBuilder.build());
				}
			}
		}

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
//					paramsBuilder.addMethod(getterBuilder.build());
//
//					MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(
//							                                             "set" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//					                                             .addModifiers(Modifier.PUBLIC)
//					                                             .addParameter(setterParameter.build());
//					setterBuilder.addStatement("this.$N.setEnumValue($N)", field.getLongName(), field.getLongName());
//					paramsBuilder.addMethod(setterBuilder.build());
//
//					getterBuilder = MethodSpec.methodBuilder(
//							                          "getFutureProof" + mJavaTool.fieldNameToClassStyle(field.getLongName()))
//					                          .returns(ParameterizedTypeName.get(futureProofEnumAccessor, typeName))
//					                          .addModifiers(Modifier.PUBLIC);
//					getterBuilder.addStatement("return $N.asReadOnly()", field.getLongName());
//					paramsBuilder.addMethod(getterBuilder.build());
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
//					paramsBuilder.addMethod(getterBuilder.build());
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
//					paramsBuilder.addMethod(setterBuilder.build());
//				}
//			}
//		}

	}

	public static class JavaField extends Field {
		private TypeName mTypeName;

		public TypeName getTypeName() {
			return mTypeName;
		}

		public void setTypeName(TypeName typeName) {
			mTypeName = typeName;
		}
	}
}
