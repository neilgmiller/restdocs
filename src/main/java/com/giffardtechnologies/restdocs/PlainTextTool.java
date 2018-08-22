package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.*;

import java.util.*;

public class PlainTextTool {

	private final Document mDocument;
	private final Set<String> mDataObjectNames;

	public PlainTextTool(Document document) {
		super();
		mDocument = document;
		
		ArrayList<DataObject> dataObjects = mDocument.getDataObjects();
		mDataObjectNames = new HashSet<String>(dataObjects.size() * 2);
		for (DataObject dataObject : dataObjects) {
			mDataObjectNames.add(dataObject.getName());
		}
	}
	
	public String fieldClass(Field field) {
		Objects.requireNonNull(field, "A field is required");
		String typeStr = getTypeString(field, field.isRequired());
		return typeStr;
	}

	private String getTypeString(TypeSpec typeSpec, boolean required) {
		DataType type = typeSpec.getType();
		if (type != null) {
			switch (type) {
				case INT:
				case LONG:
				case FLOAT:
				case DOUBLE:
				case STRING:
				case OBJECT:
				case DATE:
					return type.toString().toLowerCase(Locale.US);
				case ARRAY:
					// pass required false, since we can't use primitives
					return "array of " + getTypeString(typeSpec.getItems(), false);
			}
		} else if (typeSpec.getTyperef() != null) {
			return typeSpec.getTyperef();
		}
		return null;
	}

}
