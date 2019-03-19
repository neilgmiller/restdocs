package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.Method;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.giffardtechnologies.json.gson.BooleanDeserializer;
import org.giffardtechnologies.json.gson.LowercaseEnumTypeAdapterFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(description = "Formats Doc YAML",
		name = "yaml_cleaner", mixinStandardHelpOptions = true, version = "YAMLCleaner 1.0")
public class YAMLCleaner implements LogChute, Callable<Void> {

	@Parameters(index = "0", hidden = true, description = "The executable directory, passed by the wrapper script.")
	private File mExecutableDir;

	@Option(names = {"-f", "--file"}, required = true, description = "Flag indicating that the command should generate Java code.")
	private File mSourceFile;

	@Option(names = {"-t", "--typedata"}, description = "Flag indicating that the command should generate Java code.")
	private File mTypeSourceFile;

	public static void main(String[] args) throws IOException {
		YAMLCleaner docGenerator = new YAMLCleaner();

		CommandLine.call(docGenerator, args);
	}

	@Override
	public Void call() throws Exception {
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(mSourceFile));
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setIndent(6);
		options.setIndicatorIndent(4);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);
		Map map = yaml.load(input);
		input.close();

		Map typeMap = null;
		if (mTypeSourceFile != null) {
			input = new BufferedInputStream(new FileInputStream(mTypeSourceFile));
			typeMap = yaml.load(input);
			input.close();
		}
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory(true))
				.registerTypeAdapter(boolean.class, new BooleanDeserializer())
				.setPrettyPrinting();

		Gson gsonForPrinting = gsonBuilder.create();
		String json = gsonForPrinting.toJson(map);
//		System.out.println(json);
		PrintStream output = System.out;
		//		for (DataObject dataObject : doc.getDataObjects()) {
//			output.println(dataObject.getName());
//		}

		Document doc = gsonForPrinting.fromJson(json, Document.class);
		if (typeMap != null) {
			System.out.println("attempting type mapping");
			String json2 = gsonForPrinting.toJson(typeMap);
			Document typeDoc = gsonForPrinting.fromJson(json2, Document.class);
			for (DataObject dataObject : doc.getDataObjects()) {
				DataObject typedObject = findDataObject(dataObject.getName(), typeDoc);
				if (typedObject != null) {
					for (Field field : dataObject.getFields()) {
						Field typedField = findField(field.getName(), typedObject);
						if (typedField != null) {
							field.setLongName(typedField.getLongName());
							field.setType(typedField.getType());
							if (typedField.getItems() != null) {
								field.setItems(typedField.getItems());
							}
						}
					}
				}
			}
			for (Method method : doc.getService().getMethods()) {
				Method typedMethod = findMethod(method.getId(), typeDoc);
				if (typedMethod != null) {
					for (Field parameter : method.getParameters()) {
						Field typedField = findField(parameter.getName(), typedMethod.getParameters());
						if (typedField != null) {
							parameter.setLongName(typedField.getLongName());
							parameter.setType(typedField.getType());
							if (typedField.getItems() != null) {
								parameter.setItems(typedField.getItems());
							}
						}
					}
				}
			}
		}
		json = gsonForPrinting.toJson(doc);

		System.out.println(yaml.dump(gsonForPrinting.fromJson(json, Map.class)));

		return null;
	}

	private Method findMethod(int id, Document typeDoc) {
		for (Method method : typeDoc.getService().getMethods()) {
			if (method.getId() == id) {
				return method;
			}
		}
		return null;
	}

	private Field findField(String name, DataObject typedObject) {
		return findField(name, typedObject.getFields());
	}

	private Field findField(String name, ArrayList<Field> fields) {
		for (Field field : fields) {
			if (field.getName().equalsIgnoreCase(name)) {
				return field;
			}
		}
		return null;
	}

	private DataObject findDataObject(String name, Document typeDoc) {
		for (DataObject dataObject : typeDoc.getDataObjects()) {
			if (dataObject.getName().equalsIgnoreCase(name)) {
				return dataObject;
			}
		}
		return null;
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
