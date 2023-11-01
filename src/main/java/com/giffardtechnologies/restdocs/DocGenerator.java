package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.Method;
import com.giffardtechnologies.restdocs.domain.NamedEnumeration;
import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.tools.generic.EscapeTool;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

@Command(description = "Generates documents or code based for a given API descriptor",
		name = "doc_generator", mixinStandardHelpOptions = true, version = "DocGenerator 1.0")
public class DocGenerator implements Callable<Void> {

	//	@Parameters(index = "0", hidden = true, description = "The executable directory, passed by the wrapper script.")
	private File mExecutableDir;

	@Option(names = {"-f", "-p", "--properties"}, description = "The properties file describing the generation.")
	private File mPropertiesFile;

	@Option(names = {"-c", "--code"}, description = "Flag indicating that the command should generate Java code.")
	private boolean mGenCode = false;

	@Option(names = {"-v", "--verbose"}, description = "Turn on verbose output")
	private boolean mVerbose = false;

	private File mSourceFile;
	private File mOutputFile;

	private File mTemplateDir;
	private String mTemplateFileName;
	private Properties mProperties;

	public static void main(String[] args) throws IOException {
//		for (String arg : args) {
//			System.out.print(arg);
//			System.out.print(", ");
//		}
//		System.out.println();
		DocGenerator docGenerator = new DocGenerator();

		CommandLine.call(docGenerator, args);
	}

	@Override
	public Void call() throws Exception {
//		System.out.println(System.getProperty("execdir"));
//		System.out.println(System.getProperty("progdir"));
		mExecutableDir = new File(System.getProperty("execdir"));
		if (mPropertiesFile == null) {
			mPropertiesFile = new File("docbuild.properties");
		}

		mPropertiesFile = mPropertiesFile.getAbsoluteFile();

		mProperties = new Properties();
		BufferedInputStream propsInStream = new BufferedInputStream(new FileInputStream(mPropertiesFile));

		mProperties.load(propsInStream);
		propsInStream.close();

		File templateFile;
		String templateFilePath = mProperties.getProperty("templateFile");
		if (templateFilePath == null) {
			setTemplateDir(mExecutableDir);
		} else {
			templateFile = new File(mPropertiesFile.getParentFile(), templateFilePath);

			if (!templateFile.exists()) {
				System.err.println("No template file at: " + templateFile.getAbsolutePath());
				return null;
			}

			File parentFile = templateFile.getParentFile();
			if (parentFile == null) {
				setTemplateDir(mExecutableDir);
			} else {
				setTemplateDir(parentFile.getAbsoluteFile());
			}
			setTemplateFileName(templateFile.getName());
		}

		if (mVerbose) {
			File file = new File(mTemplateDir, mTemplateFileName);
			System.out.println("Template file is: " + file.getAbsolutePath());
		}

		setSourceFile(new File(mPropertiesFile.getParentFile(), mProperties.getProperty("sourceFile")));
		setOutputFile(new File(mPropertiesFile.getParentFile(), mProperties.getProperty("outputFile")));

		if (mGenCode) {
			generateCode();
		} else {
			generate();
		}

		return null;
	}

	public File getSourceFile() {
		return mSourceFile;
	}

	public void setSourceFile(File sourceFile) {
		mSourceFile = sourceFile;
	}

	public File getOutputFile() {
		return mOutputFile;
	}

	public void setOutputFile(File outputFile) {
		mOutputFile = outputFile;
	}

	public File getTemplateDir() {
		return mTemplateDir;
	}

	public void setTemplateDir(File templateDir) {
		mTemplateDir = templateDir;
	}

	public String getTemplateFileName() {
		return mTemplateFileName;
	}

	public void setTemplateFileName(String templateFileName) {
		mTemplateFileName = templateFileName;
	}

	private void generate() throws IOException {
		Document doc = parseDocument();

		/*
		 *  create a new instance of the engine
		 */
		VelocityEngine ve = new VelocityEngine();

		/*
		 *  initialize the engine
		 */

		Template t;
		Properties p = new Properties();
		p.setProperty("file.resource.loader.path", mTemplateDir.getAbsolutePath());

		ve.init(p);

		if (getTemplateFileName() == null) {
			t = ve.getTemplate("rest_api_doc.vm");
		} else {
			t = ve.getTemplate(getTemplateFileName());
		}

		VelocityContext context = new VelocityContext();

		context.put("document", doc);
		context.put("esc", new EscapeTool());
		context.put("link", new LinkTool(doc));
		context.put("text", new PlainTextTool(doc));

//		StringWriter sw = new StringWriter();
//		t.merge(context, sw);
//		
//		output.println(sw.getBuffer().toString());
		mOutputFile.getParentFile().mkdirs();

		FileWriter fileWriter = new FileWriter(mOutputFile);
		t.merge(context, fileWriter);
		fileWriter.close();
	}

	private void generateCode() throws IOException {
		Document doc = parseDocument();

		/*
		 *  create a new instance of the engine
		 */
		VelocityEngine ve = new VelocityEngine();

		/*
		 *  initialize the engine
		 */

		Template t;
		Properties p = new Properties();
//		p.setProperty("file.resource.loader.path", mTemplateDir.getAbsolutePath());
		p.setProperty("globbing.resource.loader.path", mTemplateDir.getAbsolutePath());
		p.setProperty("resource.loader", "globbing,string");
		p.setProperty("globbing.resource.loader.class", "org.apache.velocity.tools.view.StructuredGlobbingResourceLoader");
		p.setProperty("string.resource.loader.class", "org.apache.velocity.runtime.resource.loader.StringResourceLoader");

		ve.init(p);

		File codeDir = new File(mProperties.getProperty("codeDir"));
		// TODO check this exists

		String dtoTemplateFileName = mProperties.getProperty("dtoTemplateFile", "rest_api_dto.vm");
		t = ve.getTemplate(dtoTemplateFileName);

		VelocityContext context = new VelocityContext();
		JavaTool javaTool = new JavaTool(doc);
		context.put("java", javaTool);

		String dtoPackage = mProperties.getProperty("dtoPackage");
		String dtoPath = dtoPackage.replaceAll("\\.", "/");

		File dtoDir = new File(codeDir, dtoPath);
		dtoDir.mkdirs();

		context.put("destinationPackage", dtoPackage);
		for (DataObject dataObject : doc.getDataObjects()) {
			for (Field field : dataObject.getFields()) {
				if (field.getLongName().equalsIgnoreCase(dataObject.getName() + "id")) {
					field.setLongName("id");
				}
				if (field.getType() == DataType.OBJECT) {
					DataObject fieldDataObject = new DataObject();
					fieldDataObject.setFields(field.getFields());
					fieldDataObject.setName(javaTool.fieldToClassStyle(field));

					context.put("dataObject", fieldDataObject);

					FileWriter fileWriter = new FileWriter(new File(dtoDir, fieldDataObject.getName() + ".java"));
					t.merge(context, fileWriter);
					fileWriter.close();
				}
				if (field.getType() == DataType.ARRAY && field.getItems().getType() == DataType.OBJECT) {
					DataObject fieldDataObject = new DataObject();
					fieldDataObject.setFields(field.getItems().getFields());
					fieldDataObject.setName(javaTool.fieldToClassStyle(field));

					context.put("dataObject", fieldDataObject);

					FileWriter fileWriter = new FileWriter(new File(dtoDir, fieldDataObject.getName() + ".java"));
					t.merge(context, fileWriter);
					fileWriter.close();
				}
			}

			context.put("dataObject", dataObject);

			FileWriter fileWriter = new FileWriter(new File(dtoDir, dataObject.getName() + ".java"));
			t.merge(context, fileWriter);
			fileWriter.close();
		}

		String enumTemplateFileName = mProperties.getProperty("enumTemplateFile", "rest_api_enum.vm");
		t = ve.getTemplate(enumTemplateFileName);

		for (NamedEnumeration namedEnumeration : doc.getEnumerations()) {
			context.put("enumeration", namedEnumeration);

			FileWriter fileWriter = new FileWriter(new File(dtoDir, namedEnumeration.getName() + ".java"));
			t.merge(context, fileWriter);
			fileWriter.close();
		}

		// add the DTO package for future reference
		context.put("dtoPackage", dtoPackage);

		t = ve.getTemplate(dtoTemplateFileName);

		// create the responses
		String responsePackage = mProperties.getProperty("responsePackage");
		String responsePath = responsePackage.replaceAll("\\.", "/");

		File responseDir = new File(codeDir, responsePath);
		responseDir.mkdirs();

		context.put("destinationPackage", responsePackage);
		for (Method method : doc.getService().getMethods()) {
			if (method.getResponse() != null) {
				if (method.getResponse().getType() == DataType.OBJECT) {
					String responseClassName = method.getName().substring(0, 1).toUpperCase() + method.getName().substring(1) + "Response";

					// setup a temp DataObject
					DataObject dataObject = new DataObject();
					dataObject.setName(responseClassName);
					dataObject.setFields(method.getResponse().getFields());

					context.put("dataObject", dataObject);

					FileWriter fileWriter = new FileWriter(new File(responseDir, responseClassName + ".java"));
					t.merge(context, fileWriter);
					fileWriter.close();
				}
			}
		}

		String requestTemplateFileName = mProperties.getProperty("requestTemplateFile", "rest_api_req.vm");
		t = ve.getTemplate(requestTemplateFileName);

		String requestPackage = mProperties.getProperty("requestPackage");
		String requestPath = requestPackage.replaceAll("\\.", "/");

		File requestDir = new File(codeDir, requestPath);
		requestDir.mkdirs();

		context.put("destinationPackage", requestPackage);
		for (Method method : doc.getService().getMethods()) {
			context.put("method", method);

			FileWriter fileWriter = new FileWriter(new File(requestDir, method.getName().substring(0, 1).toUpperCase() + method.getName().substring(1) + "Request.java"));
			t.merge(context, fileWriter);
			fileWriter.close();
		}
	}

	private Document parseDocument() throws IOException {
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(mSourceFile));
		Yaml yaml = new Yaml();
//		YamlReader yamlReader = new YamlReader(new InputStreamReader(input));

//		yamlReader.read();
		Map map = (Map) yaml.load(input);
//		Document doc = yaml.loadAs(input, Document.class);
		input.close();

		GsonBuilder gsonBuilder = GsonFactory.getGsonBuilder();

		Gson gsonForPrinting = gsonBuilder.create();
		String json = gsonForPrinting.toJson(map);
//		System.out.println(json);
		PrintStream output = System.out;
		//		for (DataObject dataObject : doc.getDataObjects()) {
//			output.println(dataObject.getName());
//		}
		Document document = gsonForPrinting.fromJson(json, Document.class);

		document.buildMappings();

		return document;
	}

}
