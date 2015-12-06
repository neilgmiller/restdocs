package com.giffardtechnologies.restdocs;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

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

public class DocGenerator implements LogChute {
	
	private File mSourceFile;
	private File mOutputFile;
	
	public static void main(String[] args) throws IOException {
		DocGenerator docGenerator = new DocGenerator();
		
		File propertiesFile;
		if (args.length == 2 && args[0].equals("-f")) {
			propertiesFile = new File(args[1]);
		} else {
			propertiesFile = new File("docbuild.properties");
		}
		
		Properties properties = new Properties();
		BufferedInputStream propsInStream = new BufferedInputStream(new FileInputStream(propertiesFile));
		
		properties.load(propsInStream);
		propsInStream.close();
		
		docGenerator.setSourceFile(new File(propertiesFile.getParentFile(), properties.getProperty("sourceFile")));
		docGenerator.setOutputFile(new File(propertiesFile.getParentFile(), properties.getProperty("outputFile")));
		
		docGenerator.generate();
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
	
	private void generate() throws IOException {
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(mSourceFile));
		Yaml yaml = new Yaml();
//		YamlReader yamlReader = new YamlReader(new InputStreamReader(input));
		
//		yamlReader.read();
		Map map = (Map) yaml.load(input);
//		Document doc = yaml.loadAs(input, Document.class);
		input.close();
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory());
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
		
		ve.init();
		
		Template t = ve.getTemplate("rest_api_doc.vm");
		
		VelocityContext context = new VelocityContext();
		
		context.put("document", doc);
		context.put("esc", new EscapeTool());
		context.put("link", new LinkTool(doc));
		
		StringWriter sw = new StringWriter();
		t.merge(context, sw);
		
		output.println(sw.getBuffer().toString());
		
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
