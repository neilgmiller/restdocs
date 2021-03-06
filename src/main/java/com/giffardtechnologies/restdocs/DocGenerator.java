package com.giffardtechnologies.restdocs;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.apache.velocity.tools.generic.EscapeTool;
import org.giffardtechnologies.json.gson.BooleanDeserializer;
import org.giffardtechnologies.json.gson.LowercaseEnumTypeAdapterFactory;
import org.yaml.snakeyaml.Yaml;

import com.giffardtechnologies.restdocs.domain.Document;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "Generates documents or code based for a given API descriptor",
		name = "doc_generator", mixinStandardHelpOptions = true, version = "DocGenerator 1.0")
public class DocGenerator implements LogChute, Callable<Void> {

	@Parameters(index = "0", hidden = true, description = "The executable directory, passed by the wrapper script.")
	private File mExecutableDir;

	@Option(names = {"-f", "-p", "--properties"}, description = "The properties file describing the generation.")
	private File mPropertiesFile;

	private File mSourceFile;
	private File mOutputFile;

	private File mTemplateDir;
	private String mTemplateFileName;

	public static void main(String[] args) throws IOException {
		DocGenerator docGenerator = new DocGenerator();

		CommandLine.call(docGenerator, args);
	}

	@Override
	public Void call() throws Exception {
		if (mPropertiesFile == null) {
			mPropertiesFile = new File("docbuild.properties");
		}

		Properties properties = new Properties();
		BufferedInputStream propsInStream = new BufferedInputStream(new FileInputStream(mPropertiesFile));

		properties.load(propsInStream);
		propsInStream.close();

		File templateFile;
		String templateFilePath = properties.getProperty("templateFile");
		if (templateFilePath == null) {
			setTemplateDir(mExecutableDir);
		} else {
			templateFile = new File(mPropertiesFile.getParentFile(), templateFilePath);

			if (!templateFile.exists()) {
				System.err.println("No template file at: " + templateFile.getAbsolutePath());
				return null;
			}

			setTemplateDir(templateFile.getParentFile().getAbsoluteFile());
			setTemplateFileName(templateFile.getName());
		}

		setSourceFile(new File(mPropertiesFile.getParentFile(), properties.getProperty("sourceFile")));
		setOutputFile(new File(mPropertiesFile.getParentFile(), properties.getProperty("outputFile")));

		generate();

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
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(mSourceFile));
		Yaml yaml = new Yaml();
//		YamlReader yamlReader = new YamlReader(new InputStreamReader(input));
		
//		yamlReader.read();
		Map map = (Map) yaml.load(input);
//		Document doc = yaml.loadAs(input, Document.class);
		input.close();
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory(true));
		gsonBuilder.registerTypeAdapter(boolean.class, new BooleanDeserializer());
		gsonBuilder.setPrettyPrinting();
		Gson gsonForPrinting = gsonBuilder.create();
		String json = gsonForPrinting.toJson(map);
//		System.out.println(json);
		PrintStream output = System.out;
		Document doc = gsonForPrinting.fromJson(json, Document.class);
//		for (DataObject dataObject : doc.getDataObjects()) {
//			output.println(dataObject.getName());
//		}
		/*
		 *  create a new instance of the engine
		 */
		VelocityEngine ve = new VelocityEngine();
		
		/*
		 *  configure the engine.  In this case, we are using
		 *  ourselves as a logger (see logging examples..)
		 */
		
		ve.setProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM, this);
		
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
		
//		StringWriter sw = new StringWriter();
//		t.merge(context, sw);
//		
//		output.println(sw.getBuffer().toString());
		mOutputFile.getParentFile().mkdirs();

		FileWriter fileWriter = new FileWriter(mOutputFile);
		t.merge(context, fileWriter);
		fileWriter.close();
	}
	
	@Override
	public void init(RuntimeServices rsvc) throws Exception {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public boolean isLevelEnabled(int level) {
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public void log(int level, String message) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void log(int level, String message, Throwable t) {
		// TODO Auto-generated method stub
		
	}
}
