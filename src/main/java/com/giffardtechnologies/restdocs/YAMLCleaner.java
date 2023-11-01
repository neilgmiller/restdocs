package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.Method;
import com.giffardtechnologies.restdocs.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.velocity.runtime.RuntimeServices;
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
public class YAMLCleaner implements Callable<Void> {

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

		// setup Gson to convert to objects
		GsonBuilder gsonBuilder = GsonFactory.getGsonBuilder();
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
			ArrayList<DataObject> typedDataObjects = typeDoc.getDataObjects();
			for (DataObject dataObject : doc.getDataObjects()) {
				DataObject typedObject = findDataObject(dataObject.name, typedDataObjects);
				if (typedObject != null) {
					typedDataObjects.remove(typedObject);
					ArrayList<Field> typedObjectFields = typedObject.getFields();
					for (Field field : dataObject.getFields()) {
						Field typedField = findField(field.name, typedObjectFields);
						if (typedField != null) {
							typedObjectFields.remove(typedField);
							field.longName = typedField.longName;
							field.type = typedField.type;
							if (typedField.items != null) {
								field.items = typedField.items;
							}
						}
					}
					// add any fields that are typed, but not documented
					for (Field field : typedObjectFields) {
						dataObject.getFields().add(field);
					}
				}
			}
			// add any objects that are typed, but not documented
			for (DataObject dataObject : typedDataObjects) {
				doc.getDataObjects().add(dataObject);
			}

			for (Method method : doc.service.getMethods()) {
				Method typedMethod = findMethod(method.id, typeDoc);
				if (typedMethod != null) {
					for (Field parameter : method.getParameters()) {
						Field typedField = findField(parameter.name, typedMethod.getParameters());
						if (typedField != null) {
							parameter.longName = typedField.longName;
							parameter.type = typedField.type;
							if (typedField.items != null) {
								parameter.items = typedField.items;
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
		for (Method method : typeDoc.service.getMethods()) {
			if (method.id == id) {
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
			if (field.name.equalsIgnoreCase(name)) {
				return field;
			}
		}
		return null;
	}

	private DataObject findDataObject(String name, Document typeDoc) {
		ArrayList<DataObject> dataObjects = typeDoc.getDataObjects();
		return findDataObject(name, dataObjects);
	}

	private DataObject findDataObject(String name, ArrayList<DataObject> dataObjects) {
		for (DataObject dataObject : dataObjects) {
			if (dataObject.name.equalsIgnoreCase(name)) {
				return dataObject;
			}
		}
		return null;
	}

}
